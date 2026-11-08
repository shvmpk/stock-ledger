package com.shvmpk.stock_ledger.analytics.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ReorderListResponse(
        int salesWindowDays,
        int leadTimeDays,
        int targetCoverDays,
        int minimumHistoryDays,
        int minimumUnitsSold,
        OffsetDateTime generatedAt,
        List<ReorderItemResponse> items
) {
}
