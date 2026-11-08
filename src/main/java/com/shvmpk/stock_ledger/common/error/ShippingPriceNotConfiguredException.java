package com.shvmpk.stock_ledger.common.error;

import org.springframework.http.HttpStatus;

public class ShippingPriceNotConfiguredException extends DomainException {

    public ShippingPriceNotConfiguredException(String carrier, String parcelSize) {
        super(
                HttpStatus.BAD_REQUEST,
                "SHIPPING_PRICE_NOT_CONFIGURED",
                String.format("No active shipping rate is configured for carrier %s and parcel size %s.", carrier, parcelSize)
        );
    }
}
