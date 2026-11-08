package com.shvmpk.stock_ledger.order.dto;

import com.shvmpk.stock_ledger.order.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String orderNumber,
        String source,
        OrderStatus status,
        String customerName,
        String customerEmail,
        String shippingAddress,
        BigDecimal customerShippingCharge,
        BigDecimal actualShippingCost,
        String carrier,
        String parcelSize,
        String trackingNumber,
        String trackingUrl,
        OffsetDateTime shippedAt,
        OffsetDateTime returnedAt,
        List<OrderItemResponse> items,
        OffsetDateTime createdAt
) {
    public record OrderItemResponse(
            UUID id,
            String sku,
            int quantity,
            BigDecimal unitPrice
    ) {
    }
}
