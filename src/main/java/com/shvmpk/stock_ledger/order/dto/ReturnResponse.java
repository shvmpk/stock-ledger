package com.shvmpk.stock_ledger.order.dto;

import com.shvmpk.stock_ledger.order.entity.ItemCondition;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ReturnResponse(
        UUID id,
        UUID orderId,
        String returnReason,
        String operatorNotes,
        ProcessedByResponse processedBy,
        List<ReturnItemResponse> items,
        OffsetDateTime processedAt
) {
    public ReturnResponse(
            UUID id,
            UUID orderId,
            String returnReason,
            List<ReturnItemResponse> items,
            OffsetDateTime processedAt
    ) {
        this(id, orderId, returnReason, null, null, items, processedAt);
    }

    public record ProcessedByResponse(
            UUID id,
            String fullName,
            String email
    ) {
    }

    public record ReturnItemResponse(
            UUID id,
            UUID orderItemId,
            String sku,
            int quantity,
            ItemCondition condition,
            boolean restocked
    ) {
    }
}
