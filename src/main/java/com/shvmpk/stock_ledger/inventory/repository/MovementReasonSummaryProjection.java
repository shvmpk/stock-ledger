package com.shvmpk.stock_ledger.inventory.repository;

import com.shvmpk.stock_ledger.inventory.entity.StockMovementReason;

import java.time.OffsetDateTime;

public interface MovementReasonSummaryProjection {

    StockMovementReason getReason();

    long getMovementCount();

    long getNetOnHand();

    long getNetReserved();

    OffsetDateTime getLatestAt();
}
