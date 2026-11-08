package com.shvmpk.stock_ledger.shipping.dto;

import com.shvmpk.stock_ledger.shipping.entity.Carrier;
import com.shvmpk.stock_ledger.shipping.entity.ParcelSize;

import java.math.BigDecimal;
import java.util.UUID;

public record ShippingRateDto(
        UUID id,
        Carrier carrier,
        ParcelSize parcelSize,
        BigDecimal cost,
        boolean active
) {
}
