package com.shvmpk.stock_ledger.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record ReceiveStockRequest(
        @NotNull(message = "Product variant ID is required")
        UUID productVariantId,

        @Positive(message = "Quantity must be greater than zero")
        int quantity,

        String notes
) {}
