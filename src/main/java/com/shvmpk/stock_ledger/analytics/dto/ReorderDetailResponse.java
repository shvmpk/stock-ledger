package com.shvmpk.stock_ledger.analytics.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ReorderDetailResponse(
        UUID variantId,
        String productName,
        String optionName,
        String optionValue,
        String sku,
        int salesWindowDays,
        OffsetDateTime windowStart,
        OffsetDateTime windowEnd,
        int unitsShipped,
        int unitsReturned,
        int netUnitsSold,
        List<ReorderSaleLineResponse> sales,
        List<ReorderReturnLineResponse> returns
) {
}
