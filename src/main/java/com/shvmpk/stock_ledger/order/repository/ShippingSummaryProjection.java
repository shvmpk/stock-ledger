package com.shvmpk.stock_ledger.order.repository;

import java.math.BigDecimal;

public interface ShippingSummaryProjection {
    String getPeriod();
    long getTotalShipped();
    BigDecimal getTotalCharged();
    BigDecimal getTotalCost();
}
