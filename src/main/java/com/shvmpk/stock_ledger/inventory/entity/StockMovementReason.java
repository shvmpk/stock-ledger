package com.shvmpk.stock_ledger.inventory.entity;

public enum StockMovementReason {
    RECEIVE,
    ORDER_RESERVED,
    ORDER_SHIPPED,
    ORDER_CANCELLED,
    RETURN_RESTOCK,
    RETURN_DAMAGED,
    AUDIT_CORRECTION
}
