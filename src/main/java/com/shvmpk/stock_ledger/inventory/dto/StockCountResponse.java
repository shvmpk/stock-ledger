package com.shvmpk.stock_ledger.inventory.dto;

import com.shvmpk.stock_ledger.inventory.entity.StockCount;
import com.shvmpk.stock_ledger.inventory.entity.StockCountItem;
import com.shvmpk.stock_ledger.inventory.entity.StockCountStatus;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record StockCountResponse(
        UUID id,
        StockCountStatus status,
        StockCountOperatorResponse conductedBy,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        int itemCount,
        int totalVariance,
        boolean hasReservationDeficit,
        int reservationDeficit,
        List<StockCountItemResponse> items
) {
    public static StockCountResponse from(StockCount stockCount) {
        List<StockCountItem> sessionItems = stockCount.getItems();
        List<StockCountItemResponse> itemResponses = sessionItems.stream()
                .sorted(Comparator.comparing(item -> item.getProductVariant().getSku()))
                .map(StockCountItemResponse::from)
                .toList();

        return new StockCountResponse(
                stockCount.getId(),
                stockCount.getStatus(),
                StockCountOperatorResponse.from(stockCount.getConductedBy()),
                stockCount.getStartedAt(),
                stockCount.getCompletedAt(),
                itemResponses.size(),
                sessionItems.stream().mapToInt(StockCountItem::getVariance).sum(),
                sessionItems.stream().anyMatch(StockCountItem::isHasReservationDeficit),
                sessionItems.stream().mapToInt(StockCountItem::getReservationDeficit).sum(),
                itemResponses
        );
    }
}
