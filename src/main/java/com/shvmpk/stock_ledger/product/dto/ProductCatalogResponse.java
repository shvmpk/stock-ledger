package com.shvmpk.stock_ledger.product.dto;

import java.util.List;
import java.util.UUID;

public record ProductCatalogResponse(
        UUID id,
        String name,
        String description,
        String category,
        boolean active,
        List<ProductVariantStockResponse> variants
) {}
