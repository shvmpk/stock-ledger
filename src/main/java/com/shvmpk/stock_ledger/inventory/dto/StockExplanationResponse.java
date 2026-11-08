package com.shvmpk.stock_ledger.inventory.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record StockExplanationResponse(
        UUID productVariantId,
        String sku,
        String productName,
        String optionName,
        String optionValue,
        int onHandQuantity,
        int reservedQuantity,
        int availableQuantity,
        Integer reorderLevel,
        boolean sellable,
        int movementWindowDays,
        OffsetDateTime windowStart,
        List<ReservationHolderResponse> reservations,
        List<MovementSummaryResponse> recentMovements,
        List<String> sentences,
        OffsetDateTime generatedAt
) {
}
