package com.shvmpk.stock_ledger.inventory.dto;

import com.shvmpk.stock_ledger.inventory.entity.StockCountItem;

import java.util.UUID;

public record StockCountItemResponse(
        UUID id,
        UUID productVariantId,
        String sku,
        String productName,
        String optionName,
        String optionValue,
        int expectedOnHand,
        int countedOnHand,
        int variance,
        String notes,
        boolean hasReservationDeficit,
        int reservationDeficit
) {
    public static StockCountItemResponse from(StockCountItem item) {
        return new StockCountItemResponse(
                item.getId(),
                item.getProductVariant().getId(),
                item.getProductVariant().getSku(),
                item.getProductVariant().getProduct().getName(),
                item.getProductVariant().getOptionName(),
                item.getProductVariant().getOptionValue(),
                item.getExpectedOnHand(),
                item.getCountedOnHand(),
                item.getVariance(),
                item.getNotes(),
                item.isHasReservationDeficit(),
                item.getReservationDeficit()
        );
    }
}
