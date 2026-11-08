package com.shvmpk.stock_ledger.analytics.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReorderReturnLineResponse(
        UUID orderId,
        String orderNumber,
        OffsetDateTime processedAt,
        int quantity,
        String condition
) {
}
