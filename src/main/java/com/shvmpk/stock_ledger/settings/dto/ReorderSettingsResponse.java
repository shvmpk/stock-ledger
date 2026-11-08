package com.shvmpk.stock_ledger.settings.dto;

import java.time.OffsetDateTime;

public record ReorderSettingsResponse(
        int leadTimeDays,
        OffsetDateTime updatedAt
) {
}
