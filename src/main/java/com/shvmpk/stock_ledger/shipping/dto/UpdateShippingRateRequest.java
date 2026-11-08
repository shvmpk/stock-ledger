package com.shvmpk.stock_ledger.shipping.dto;

import com.shvmpk.stock_ledger.shipping.entity.Carrier;
import com.shvmpk.stock_ledger.shipping.entity.ParcelSize;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateShippingRateRequest(
        @NotNull(message = "Carrier is required")
        Carrier carrier,

        @NotNull(message = "Parcel size is required")
        ParcelSize parcelSize,

        @NotNull(message = "Cost is required")
        @DecimalMin(value = "0.00", message = "Cost cannot be negative")
        BigDecimal cost,

        boolean active
) {
}
