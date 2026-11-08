package com.shvmpk.stock_ledger.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record CreateOrderRequest(
        @NotBlank(message = "Customer name is required")
        String customerName,

        @Email(message = "Customer email must be valid")
        String customerEmail,

        @NotBlank(message = "Shipping address is required")
        String shippingAddress,

        @NotNull(message = "Customer shipping charge is required")
        @DecimalMin(value = "0.00", message = "Customer shipping charge cannot be negative")
        BigDecimal customerShippingCharge,

        @NotEmpty(message = "Order must contain at least one item")
        List<@Valid CreateOrderItemRequest> items
) {
}
