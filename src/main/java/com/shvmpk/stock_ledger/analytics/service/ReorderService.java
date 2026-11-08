package com.shvmpk.stock_ledger.analytics.service;

import com.shvmpk.stock_ledger.analytics.dto.ReorderDetailResponse;
import com.shvmpk.stock_ledger.analytics.dto.ReorderItemResponse;
import com.shvmpk.stock_ledger.analytics.dto.ReorderListResponse;
import com.shvmpk.stock_ledger.analytics.dto.ReorderReturnLineResponse;
import com.shvmpk.stock_ledger.analytics.dto.ReorderSaleLineResponse;
import com.shvmpk.stock_ledger.analytics.repository.ReorderSalesRepository;
import com.shvmpk.stock_ledger.common.error.ResourceNotFoundException;
import com.shvmpk.stock_ledger.inventory.entity.InventoryLevel;
import com.shvmpk.stock_ledger.inventory.repository.InventoryRepository;
import com.shvmpk.stock_ledger.product.entity.ProductVariant;
import com.shvmpk.stock_ledger.product.repository.ProductVariantRepository;
import com.shvmpk.stock_ledger.settings.dto.ReorderSettingsResponse;
import com.shvmpk.stock_ledger.settings.service.ReorderSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReorderService {

    private final InventoryRepository inventoryRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ReorderSalesRepository reorderSalesRepository;
    private final ReorderSettingsService reorderSettingsService;
    private final ReorderCalculator reorderCalculator;

    @Transactional(readOnly = true)
    public ReorderListResponse getReorderList() {
        ReorderSettingsResponse settings = reorderSettingsService.getSettings();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime windowStart = now.minusDays(ReorderCalculator.SALES_WINDOW_DAYS);

        Map<UUID, ReorderSalesRepository.VariantSales> salesByVariant =
                reorderSalesRepository.findVariantSales(windowStart).stream()
                        .collect(Collectors.toMap(
                                ReorderSalesRepository.VariantSales::variantId,
                                Function.identity()
                        ));
        Map<UUID, Integer> returnsByVariant =
                reorderSalesRepository.findVariantReturns(windowStart).stream()
                        .collect(Collectors.toMap(
                                ReorderSalesRepository.VariantReturns::variantId,
                                ReorderSalesRepository.VariantReturns::unitsReturned
                        ));

        List<ReorderItemResponse> items = inventoryRepository.findAllSellableWithVariantAndProduct().stream()
                .map(level -> toItem(
                        level,
                        salesByVariant.get(level.getProductVariant().getId()),
                        returnsByVariant.getOrDefault(level.getProductVariant().getId(), 0),
                        settings.leadTimeDays(),
                        now
                ))
                .flatMap(Optional::stream)
                .sorted(urgencyOrder())
                .toList();

        return new ReorderListResponse(
                ReorderCalculator.SALES_WINDOW_DAYS,
                settings.leadTimeDays(),
                ReorderCalculator.TARGET_COVER_DAYS,
                ReorderCalculator.MINIMUM_HISTORY_DAYS,
                ReorderCalculator.MINIMUM_UNITS_SOLD,
                now,
                items
        );
    }

    @Transactional(readOnly = true)
    public ReorderDetailResponse getDetail(UUID variantId) {
        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("ProductVariant", variantId));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime windowStart = now.minusDays(ReorderCalculator.SALES_WINDOW_DAYS);

        List<ReorderSalesRepository.SaleLine> saleLines =
                reorderSalesRepository.findSaleLines(variantId, windowStart, now);
        List<ReorderSalesRepository.ReturnLine> returnLines =
                reorderSalesRepository.findReturnLines(variantId, windowStart, now);

        int unitsShipped = saleLines.stream()
                .mapToInt(ReorderSalesRepository.SaleLine::quantity)
                .sum();
        int unitsReturned = returnLines.stream()
                .mapToInt(ReorderSalesRepository.ReturnLine::quantity)
                .sum();

        return new ReorderDetailResponse(
                variant.getId(),
                variant.getProduct().getName(),
                variant.getOptionName(),
                variant.getOptionValue(),
                variant.getSku(),
                ReorderCalculator.SALES_WINDOW_DAYS,
                windowStart,
                now,
                unitsShipped,
                unitsReturned,
                Math.max(0, unitsShipped - unitsReturned),
                saleLines.stream()
                        .map(line -> new ReorderSaleLineResponse(
                                line.orderId(),
                                line.orderNumber(),
                                line.shippedAt(),
                                line.quantity(),
                                line.status()
                        ))
                        .toList(),
                returnLines.stream()
                        .map(line -> new ReorderReturnLineResponse(
                                line.orderId(),
                                line.orderNumber(),
                                line.processedAt(),
                                line.quantity(),
                                line.condition()
                        ))
                        .toList()
        );
    }

    private Optional<ReorderItemResponse> toItem(
            InventoryLevel level,
            ReorderSalesRepository.VariantSales sales,
            int unitsReturned,
            int leadTimeDays,
            OffsetDateTime now
    ) {
        ProductVariant variant = level.getProductVariant();
        int unitsShipped = sales != null ? sales.unitsShipped() : 0;
        int unitsSold = Math.max(0, unitsShipped - unitsReturned);
        OffsetDateTime firstShippedAt = sales != null ? sales.firstShippedAt() : null;
        int available = level.getAvailableQuantity();
        Integer reorderLevel = variant.getMinStockThreshold();

        ReorderEstimate estimate = reorderCalculator.estimate(
                available,
                unitsSold,
                firstShippedAt,
                leadTimeDays,
                now
        );
        if (!reorderCalculator.needsAction(estimate, available, reorderLevel)) {
            return Optional.empty();
        }

        return Optional.of(new ReorderItemResponse(
                variant.getId(),
                variant.getProduct().getId(),
                variant.getProduct().getName(),
                variant.getOptionName(),
                variant.getOptionValue(),
                variant.getSku(),
                variant.getBarcode(),
                level.getOnHandQuantity(),
                level.getReservedQuantity(),
                available,
                reorderLevel,
                unitsSold,
                sales != null ? sales.ordersCount() : 0,
                firstShippedAt,
                estimate.daysOfCover(),
                estimate.suggestedQuantity(),
                estimate.historyStatus()
        ));
    }

    private Comparator<ReorderItemResponse> urgencyOrder() {
        return Comparator
                .comparingInt(ReorderService::urgencyGroup)
                .thenComparingInt(item -> item.daysOfCover() == null
                        ? Integer.MAX_VALUE
                        : item.daysOfCover())
                .thenComparingInt(ReorderItemResponse::available)
                .thenComparing(ReorderItemResponse::sku);
    }

    private static int urgencyGroup(ReorderItemResponse item) {
        if (item.available() <= 0) {
            return 0;
        }
        if (item.suggestedQuantity() != null && item.suggestedQuantity() > 0) {
            return 1;
        }
        return 2;
    }
}
