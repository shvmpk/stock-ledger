package com.shvmpk.stock_ledger.inventory.dto;

import com.shvmpk.stock_ledger.inventory.entity.StockCount;
import com.shvmpk.stock_ledger.inventory.entity.StockCountItem;
import com.shvmpk.stock_ledger.inventory.entity.StockCountStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record StockCountSummaryResponse(
        UUID id,
        StockCountStatus status,
        StockCountOperatorResponse conductedBy,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        int itemCount,
        int totalVariance,
        boolean hasReservationDeficit,
        int reservationDeficit
) {
    public static StockCountSummaryResponse from(StockCount stockCount) {
        List<StockCountItem> sessionItems = stockCount.getItems();

        return new StockCountSummaryResponse(
                stockCount.getId(),
                stockCount.getStatus(),
                StockCountOperatorResponse.from(stockCount.getConductedBy()),
                stockCount.getStartedAt(),
                stockCount.getCompletedAt(),
                sessionItems.size(),
                sessionItems.stream().mapToInt(StockCountItem::getVariance).sum(),
                sessionItems.stream().anyMatch(StockCountItem::isHasReservationDeficit),
                sessionItems.stream().mapToInt(StockCountItem::getReservationDeficit).sum()
        );
    }
}
