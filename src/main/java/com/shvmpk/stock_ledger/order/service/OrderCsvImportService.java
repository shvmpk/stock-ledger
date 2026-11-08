package com.shvmpk.stock_ledger.order.service;

import com.shvmpk.stock_ledger.order.dto.CsvImportResult;
import com.shvmpk.stock_ledger.order.entity.Order;
import com.shvmpk.stock_ledger.order.entity.OrderStatus;
import com.shvmpk.stock_ledger.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCsvImportService {

    private final OrderRowImportProcessor orderRowImportProcessor;
    private final BacklogAllocationService backlogAllocationService;
    private final OrderRepository orderRepository;

    public CsvImportResult importOrdersFromCsv(InputStream inputStream) {
        int totalRows = 0;
        int importedOrders = 0;
        int waitingForStockOrders = 0;
        int failedRows = 0;
        List<CsvImportResult.CsvRowError> errors = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return new CsvImportResult(0, 0, 0, 0, List.of());
            }

            String line;
            int currentLineNumber = 1;

            while ((line = reader.readLine()) != null) {
                currentLineNumber++;
                if (line.trim().isEmpty()) {
                    continue;
                }

                totalRows++;
                List<String> tokens = parseCsvLine(line);
                if (tokens.size() < 8) {
                    failedRows++;
                    errors.add(new CsvImportResult.CsvRowError(
                            totalRows,
                            tokens.isEmpty() ? "UNKNOWN" : tokens.getFirst(),
                            "UNKNOWN",
                            "Malformed CSV row: expected 8 columns but found " + tokens.size()
                    ));
                    continue;
                }

                String orderNumber = tokens.get(0);
                String customerName = tokens.get(1);
                String customerEmail = tokens.get(2);
                String shippingAddress = tokens.get(3);
                String shippingChargeStr = tokens.get(4);
                String sku = tokens.get(5);
                String quantityStr = tokens.get(6);
                String unitPriceStr = tokens.get(7);

                try {
                    BigDecimal shippingCharge = new BigDecimal(shippingChargeStr);
                    int quantity = Integer.parseInt(quantityStr);
                    BigDecimal unitPrice = new BigDecimal(unitPriceStr);

                    Order order = orderRowImportProcessor.createWaitingOrder(
                            orderNumber,
                            customerName,
                            customerEmail,
                            shippingAddress,
                            shippingCharge,
                            sku,
                            quantity,
                            unitPrice
                    );
                    importedOrders++;

                    boolean allocated = allocate(order);
                    if (!allocated) {
                        waitingForStockOrders++;
                    }
                } catch (Exception ex) {
                    log.warn("Failed to import CSV order row {}: {}", currentLineNumber, ex.getMessage());
                    failedRows++;
                    errors.add(new CsvImportResult.CsvRowError(
                            totalRows,
                            orderNumber,
                            sku,
                            ex.getMessage()
                    ));
                }
            }
        } catch (Exception ex) {
            log.error("Error reading CSV input stream", ex);
            throw new RuntimeException("Failed to read CSV input stream", ex);
        }

        return new CsvImportResult(totalRows, importedOrders, waitingForStockOrders, failedRows, errors);
    }

    private boolean allocate(Order order) {
        try {
            var variantId = order.getItems().getFirst().getProductVariant().getId();
            backlogAllocationService.allocateWaitingForVariant(variantId, null);
        } catch (RuntimeException ex) {
            log.error(
                    "CSV order {} was imported but backlog allocation failed. It will remain waiting.",
                    order.getOrderNumber(),
                    ex
            );
        }

        return orderRepository.findById(order.getId())
                .map(found -> found.getStatus() != OrderStatus.WAITING_FOR_STOCK)
                .orElse(false);
    }

    public static List<String> parseCsvLine(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                tokens.add(cleanToken(sb.toString()));
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(cleanToken(sb.toString()));
        return tokens;
    }

    private static String cleanToken(String token) {
        String trimmed = token.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }
}
