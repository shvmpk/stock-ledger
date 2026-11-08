package com.shvmpk.stock_ledger.shipping.service;

import com.shvmpk.stock_ledger.shipping.entity.Carrier;
import org.springframework.stereotype.Component;

@Component
public class CarrierTrackingResolver {

    public String resolveTrackingUrl(Carrier carrier, String trackingNumber) {
        if (carrier == null) {
            throw new IllegalArgumentException("Carrier cannot be null");
        }
        if (trackingNumber == null || trackingNumber.isBlank()) {
            throw new IllegalArgumentException("Tracking number cannot be blank");
        }

        String trimmed = trackingNumber.trim();
        return switch (carrier) {
            case DHL -> "https://www.dhl.com/en/express/tracking.html?AWB=" + trimmed;
            case FEDEX -> "https://www.fedex.com/fedextrack/?trknbr=" + trimmed;
            case UPS -> "https://www.ups.com/track?tracknum=" + trimmed;
            case ROYAL_MAIL -> "https://www.royalmail.com/track-your-item#/tracking-results/" + trimmed;
        };
    }
}
