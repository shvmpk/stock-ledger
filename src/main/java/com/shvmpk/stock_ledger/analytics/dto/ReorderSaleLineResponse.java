package com.shvmpk.stock_ledger.analytics.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReorderSaleLineResponse(
        UUID orderId,
        String orderNumber,
        OffsetDateTime shippedAt,
        int quantity,
        String status
) {
}
