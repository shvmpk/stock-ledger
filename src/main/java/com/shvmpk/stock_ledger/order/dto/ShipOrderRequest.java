package com.shvmpk.stock_ledger.order.dto;

import com.shvmpk.stock_ledger.shipping.entity.Carrier;
import com.shvmpk.stock_ledger.shipping.entity.ParcelSize;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ShipOrderRequest(
        @NotNull(message = "Carrier is required")
        Carrier carrier,

        @NotNull(message = "Parcel size is required")
        ParcelSize parcelSize,

        @NotBlank(message = "Tracking number is required")
        String trackingNumber,

        @DecimalMin(value = "0.00", message = "Actual shipping cost override cannot be negative")
        @Digits(integer = 8, fraction = 2, message = "Actual shipping cost override must have at most 2 decimals")
        BigDecimal actualShippingCostOverride
) {
    public ShipOrderRequest(Carrier carrier, ParcelSize parcelSize, String trackingNumber) {
        this(carrier, parcelSize, trackingNumber, null);
    }
}
