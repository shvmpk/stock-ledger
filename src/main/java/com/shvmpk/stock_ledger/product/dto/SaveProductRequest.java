package com.shvmpk.stock_ledger.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SaveProductRequest(
        @NotBlank(message = "Product name is required")
        @Size(max = 255, message = "Product name must be 255 characters or fewer")
        String name,

        @Size(max = 5000, message = "Description must be 5000 characters or fewer")
        String description,

        @Size(max = 100, message = "Category must be 100 characters or fewer")
        String category,

        @NotEmpty(message = "Add at least one variant")
        List<@Valid SaveProductVariantRequest> variants
) {
}
