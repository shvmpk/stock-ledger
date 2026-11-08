package com.shvmpk.stock_ledger.product.dto;

import java.util.UUID;

public record LowStockVariantResponse(
        UUID variantId,
        String productName,
        String sku,
        String barcode,
        String optionValue,
        int onHand,
        int reserved,
        int available,
        int minStockThreshold
) {}
