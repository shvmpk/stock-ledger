package com.shvmpk.stock_ledger.export.service;

import com.shvmpk.stock_ledger.inventory.entity.InventoryLevel;
import com.shvmpk.stock_ledger.inventory.entity.StockMovement;
import com.shvmpk.stock_ledger.inventory.repository.InventoryRepository;
import com.shvmpk.stock_ledger.inventory.repository.StockMovementRepository;
import com.shvmpk.stock_ledger.order.entity.Order;
import com.shvmpk.stock_ledger.order.repository.OrderRepository;
import com.shvmpk.stock_ledger.product.entity.Product;
import com.shvmpk.stock_ledger.product.entity.ProductVariant;
import com.shvmpk.stock_ledger.product.repository.ProductRepository;
import com.shvmpk.stock_ledger.product.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DataExportService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryRepository inventoryRepository;
    private final OrderRepository orderRepository;
    private final StockMovementRepository stockMovementRepository;

    @Transactional(readOnly = true)
    public String exportCatalogCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append("Product Name,SKU,Barcode,Option Name,Option Value,Wholesale Cost,Retail Price,Weight Grams,On Hand,Reserved,Available\n");

        List<ProductVariant> variants = productVariantRepository.findAll();
        Map<UUID, InventoryLevel> inventoryMap = inventoryRepository.findAll().stream()
                .collect(Collectors.toMap(inv -> inv.getProductVariant().getId(), Function.identity(), (a, b) -> a));

        for (ProductVariant v : variants) {
            String productName = v.getProduct() != null ? v.getProduct().getName() : "";
            InventoryLevel inv = inventoryMap.get(v.getId());
            int onHand = inv != null ? inv.getOnHandQuantity() : 0;
            int reserved = inv != null ? inv.getReservedQuantity() : 0;
            int available = inv != null ? (onHand - reserved) : 0;

            sb.append(escapeCsv(productName)).append(",")
                    .append(escapeCsv(v.getSku())).append(",")
                    .append(escapeCsv(v.getBarcode())).append(",")
                    .append(escapeCsv(v.getOptionName())).append(",")
                    .append(escapeCsv(v.getOptionValue())).append(",")
                    .append(v.getCostPrice() != null ? v.getCostPrice() : "").append(",")
                    .append(v.getSellingPrice() != null ? v.getSellingPrice() : "").append(",")
                    .append(v.getWeightGrams() != null ? v.getWeightGrams() : "").append(",")
                    .append(onHand).append(",")
                    .append(reserved).append(",")
                    .append(available).append("\n");
        }

        return sb.toString();
    }

    @Transactional(readOnly = true)
    public String exportOrdersCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append("Order Number,Source,Status,Customer Name,Customer Email,Shipping Address,Carrier,Tracking Number,Shipping Charge,Actual Cost,Shipped At,Created At\n");

        List<Order> orders = orderRepository.findAll();
        for (Order o : orders) {
            sb.append(escapeCsv(o.getOrderNumber())).append(",")
                    .append(o.getSource() != null ? o.getSource().name() : "").append(",")
                    .append(o.getStatus() != null ? o.getStatus().name() : "").append(",")
                    .append(escapeCsv(o.getCustomerName())).append(",")
                    .append(escapeCsv(o.getCustomerEmail())).append(",")
                    .append(escapeCsv(o.getShippingAddress())).append(",")
                    .append(escapeCsv(o.getCarrier())).append(",")
                    .append(escapeCsv(o.getTrackingNumber())).append(",")
                    .append(o.getCustomerShippingCharge() != null ? o.getCustomerShippingCharge() : "").append(",")
                    .append(o.getActualShippingCost() != null ? o.getActualShippingCost() : "").append(",")
                    .append(o.getShippedAt() != null ? o.getShippedAt().toString() : "").append(",")
                    .append(o.getCreatedAt() != null ? o.getCreatedAt().toString() : "").append("\n");
        }

        return sb.toString();
    }

    @Transactional(readOnly = true)
    public String exportStockHistoryCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append("Movement ID,Product Variant ID,Delta On Hand,Delta Reserved,Reason,Reference Type,Reference ID,Notes,Created At\n");

        List<StockMovement> movements = stockMovementRepository.findAll();
        for (StockMovement m : movements) {
            sb.append(escapeCsv(m.getId().toString())).append(",")
                    .append(escapeCsv(m.getProductVariant().getId().toString())).append(",")
                    .append(m.getDeltaOnHand()).append(",")
                    .append(m.getDeltaReserved()).append(",")
                    .append(m.getReason() != null ? m.getReason().name() : "").append(",")
                    .append(escapeCsv(m.getReferenceType())).append(",")
                    .append(m.getReferenceId() != null ? m.getReferenceId().toString() : "").append(",")
                    .append(escapeCsv(m.getNotes())).append(",")
                    .append(m.getCreatedAt() != null ? m.getCreatedAt().toString() : "").append("\n");
        }

        return sb.toString();
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
