package com.shvmpk.stock_ledger.inventory.dto;

import com.shvmpk.stock_ledger.order.entity.OrderStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReservationHolderResponse(
        UUID orderId,
        String orderNumber,
        OrderStatus status,
        int reservedQuantity,
        OffsetDateTime orderedAt
) {
}
