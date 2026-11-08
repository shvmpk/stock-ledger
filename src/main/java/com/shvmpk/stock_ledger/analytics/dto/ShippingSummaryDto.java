package com.shvmpk.stock_ledger.analytics.dto;

import java.math.BigDecimal;

public record ShippingSummaryDto(
        String period,
        long totalOrdersShipped,
        BigDecimal totalShippingCharged,
        BigDecimal totalShippingCost,
        BigDecimal netMargin
) {
    public ShippingSummaryDto {
        if (totalShippingCharged == null) totalShippingCharged = BigDecimal.ZERO;
        if (totalShippingCost == null) totalShippingCost = BigDecimal.ZERO;
        if (netMargin == null) netMargin = totalShippingCharged.subtract(totalShippingCost);
    }
}
