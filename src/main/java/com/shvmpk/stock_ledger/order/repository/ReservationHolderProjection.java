package com.shvmpk.stock_ledger.order.repository;

import com.shvmpk.stock_ledger.order.entity.OrderStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface ReservationHolderProjection {

    UUID getOrderId();

    String getOrderNumber();

    OrderStatus getStatus();

    int getReservedQuantity();

    OffsetDateTime getOrderDate();
}
