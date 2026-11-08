package com.shvmpk.stock_ledger.inventory.dto;

import com.shvmpk.stock_ledger.inventory.entity.InventoryLevel;

import java.util.UUID;

public record StockLevelResponse(
        UUID id,
        UUID productVariantId,
        String sku,
        int onHandQuantity,
        int reservedQuantity,
        int availableQuantity
) {
    public static StockLevelResponse from(InventoryLevel level) {
        return new StockLevelResponse(
                level.getId(),
                level.getProductVariant().getId(),
                level.getProductVariant().getSku(),
                level.getOnHandQuantity(),
                level.getReservedQuantity(),
                level.getAvailableQuantity()
        );
    }
}
