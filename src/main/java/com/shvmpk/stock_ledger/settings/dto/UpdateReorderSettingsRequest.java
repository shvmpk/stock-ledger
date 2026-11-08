package com.shvmpk.stock_ledger.settings.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateReorderSettingsRequest(
        @NotNull(message = "Supplier lead time is required")
        @Min(value = 1, message = "Supplier lead time must be at least 1 day")
        @Max(value = 365, message = "Supplier lead time cannot exceed 365 days")
        Integer leadTimeDays
) {
}
