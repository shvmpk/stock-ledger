package com.shvmpk.stock_ledger.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record SaveProductVariantRequest(
        UUID id,

        @NotBlank(message = "SKU is required")
        @Size(max = 100, message = "SKU must be 100 characters or fewer")
        String sku,

        @Size(max = 100, message = "Barcode must be 100 characters or fewer")
        String barcode,

        @Size(max = 100, message = "Option name must be 100 characters or fewer")
        String optionName,

        @Size(max = 100, message = "Option value must be 100 characters or fewer")
        String optionValue,

        @DecimalMin(value = "0.00", message = "Cost price cannot be negative")
        @Digits(integer = 8, fraction = 2, message = "Cost price must have at most 8 digits and 2 decimals")
        BigDecimal costPrice,

        @NotNull(message = "Retail price is required")
        @DecimalMin(value = "0.00", message = "Selling price cannot be negative")
        @Digits(integer = 8, fraction = 2, message = "Selling price must have at most 8 digits and 2 decimals")
        BigDecimal sellingPrice,

        @PositiveOrZero(message = "Weight cannot be negative")
        Integer weightGrams,

        @PositiveOrZero(message = "Reorder level cannot be negative")
        Integer minStockThreshold
) {
}
