package com.shvmpk.stock_ledger.inventory.service;

import java.util.UUID;

public record StockAuditResult(
        UUID variantId,
        int previousOnHand,
        int countedOnHand,
        int variance,
        boolean hasReservationDeficit,
        int reservationDeficit
) {
}
