package com.shvmpk.stock_ledger.platform.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

public record WebhookOrderPayload(
        @NotBlank(message = "External order ID is required")
        String externalOrderId,

        @NotBlank(message = "Order number is required")
        String orderNumber,

        @NotBlank(message = "Customer name is required")
        String customerName,

        String customerEmail,

        @NotBlank(message = "Shipping address is required")
        String shippingAddress,

        @NotNull(message = "Shipping charge is required")
        BigDecimal shippingCharge,

        @NotEmpty(message = "Items cannot be empty")
        List<@Valid WebhookOrderItem> items
) {
    public record WebhookOrderItem(
            @NotBlank(message = "SKU is required")
            String sku,

            @NotNull(message = "Quantity is required")
            @Positive(message = "Quantity must be positive")
            Integer quantity,

            @NotNull(message = "Unit price is required")
            BigDecimal unitPrice
    ) {
    }
}
