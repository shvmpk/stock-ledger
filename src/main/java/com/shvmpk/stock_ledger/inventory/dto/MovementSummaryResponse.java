package com.shvmpk.stock_ledger.inventory.dto;

import com.shvmpk.stock_ledger.inventory.entity.StockMovementReason;

import java.time.OffsetDateTime;

public record MovementSummaryResponse(
        StockMovementReason reason,
        String label,
        int movementCount,
        int netOnHand,
        int netReserved,
        OffsetDateTime latestAt
) {
}
