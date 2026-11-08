package com.shvmpk.stock_ledger.order.dto;

import com.shvmpk.stock_ledger.order.entity.OrderStatus;

public record OrderStatusSummaryResponse(
        OrderStatus status,
        long count
) {
}
