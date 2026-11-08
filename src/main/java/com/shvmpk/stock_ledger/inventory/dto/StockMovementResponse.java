package com.shvmpk.stock_ledger.inventory.dto;

import com.shvmpk.stock_ledger.inventory.entity.StockMovement;
import com.shvmpk.stock_ledger.inventory.entity.StockMovementReason;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StockMovementResponse(
        UUID id,
        UUID productVariantId,
        String sku,
        String productName,
        int deltaOnHand,
        int deltaReserved,
        StockMovementReason reason,
        String referenceType,
        UUID referenceId,
        String notes,
        String actorName,
        OffsetDateTime createdAt
) {
    public static StockMovementResponse from(StockMovement movement) {
        return new StockMovementResponse(
                movement.getId(),
                movement.getProductVariant().getId(),
                movement.getSku(),
                movement.getProductName(),
                movement.getDeltaOnHand(),
                movement.getDeltaReserved(),
                movement.getReason(),
                movement.getReferenceType(),
                movement.getReferenceId(),
                movement.getNotes(),
                movement.getActor() != null ? movement.getActor().getFullName() : null,
                movement.getCreatedAt()
        );
    }
}
