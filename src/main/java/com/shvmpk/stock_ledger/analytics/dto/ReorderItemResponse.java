package com.shvmpk.stock_ledger.analytics.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReorderItemResponse(
        UUID variantId,
        UUID productId,
        String productName,
        String optionName,
        String optionValue,
        String sku,
        String barcode,
        int onHand,
        int reserved,
        int available,
        Integer reorderLevel,
        int unitsSold,
        int ordersCount,
        OffsetDateTime firstSoldAt,
        Integer daysOfCover,
        Integer suggestedQuantity,
        ReorderHistoryStatus historyStatus
) {
}
