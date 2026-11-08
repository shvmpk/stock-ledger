package com.shvmpk.stock_ledger.order.entity;

public enum OrderStatus {
    PENDING,
    WAITING_FOR_STOCK,
    READY_TO_SHIP,
    SHIPPED,
    RETURNED,
    PARTIALLY_RETURNED,
    CANCELLED
}
