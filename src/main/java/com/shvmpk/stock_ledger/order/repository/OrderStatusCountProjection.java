package com.shvmpk.stock_ledger.order.repository;

import com.shvmpk.stock_ledger.order.entity.OrderStatus;

public interface OrderStatusCountProjection {

    OrderStatus getStatus();

    long getCount();
}
