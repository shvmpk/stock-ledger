package com.shvmpk.stock_ledger.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record RecordStockCountItemRequest(
        @NotNull(message = "Product variant ID is required")
        UUID productVariantId,

        @NotNull(message = "Counted on-hand quantity is required")
        @PositiveOrZero(message = "Counted on-hand cannot be negative")
        Integer countedOnHand,

        String notes
) {
}
