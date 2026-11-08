package com.shvmpk.stock_ledger.product.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductVariantStockResponse(
        UUID id,
        String sku,
        String barcode,
        String optionName,
        String optionValue,
        boolean active,
        // Omitted for STAFF; owners receive the wholesale cost. See AGENTS.md §11.
        @JsonInclude(JsonInclude.Include.NON_NULL)
        BigDecimal costPrice,
        BigDecimal sellingPrice,
        Integer weightGrams,
        Integer minStockThreshold,
        int onHand,
        int reserved,
        int available,
        boolean isLowStock
) {}
