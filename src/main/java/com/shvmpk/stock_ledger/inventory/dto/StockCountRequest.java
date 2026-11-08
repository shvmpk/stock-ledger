package com.shvmpk.stock_ledger.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record StockCountRequest(
        @NotNull(message = "Product variant ID is required")
        UUID productVariantId,

        @PositiveOrZero(message = "Counted on-hand cannot be negative")
        int countedOnHand,

        String notes
) {}
