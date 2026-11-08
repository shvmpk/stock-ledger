package com.shvmpk.stock_ledger.platform.adapter;

import java.math.BigDecimal;
import java.util.List;

public record ExternalOrder(
        String externalOrderId,
        String orderNumber,
        String customerName,
        String customerEmail,
        String shippingAddress,
        BigDecimal shippingCharge,
        String currency,
        List<ExternalOrderItem> items
) {
    public record ExternalOrderItem(
            String sku,
            int quantity,
            BigDecimal unitPrice
    ) {
    }
}
