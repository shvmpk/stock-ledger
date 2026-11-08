package com.shvmpk.stock_ledger.unit.shipping;

import com.shvmpk.stock_ledger.shipping.entity.Carrier;
import com.shvmpk.stock_ledger.shipping.service.CarrierTrackingResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CarrierTrackingResolver Unit Tests")
class CarrierTrackingResolverTest {

    private final CarrierTrackingResolver resolver = new CarrierTrackingResolver();

    @ParameterizedTest
    @CsvSource({
            "DHL, 1234567890, https://www.dhl.com/en/express/tracking.html?AWB=1234567890",
            "FEDEX, 794612345678, https://www.fedex.com/fedextrack/?trknbr=794612345678",
            "UPS, 1Z9999999999999999, https://www.ups.com/track?tracknum=1Z9999999999999999",
            "ROYAL_MAIL, GB123456789GB, https://www.royalmail.com/track-your-item#/tracking-results/GB123456789GB"
    })
    @DisplayName("Should resolve correct tracking URL for standard carriers")
    void shouldResolveTrackingUrl(Carrier carrier, String trackingNumber, String expectedUrl) {
        String result = resolver.resolveTrackingUrl(carrier, trackingNumber);
        assertThat(result).isEqualTo(expectedUrl);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("Should reject blank or null tracking number")
    void shouldRejectBlankTrackingNumber(String trackingNumber) {
        assertThatThrownBy(() -> resolver.resolveTrackingUrl(Carrier.DHL, trackingNumber))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tracking number cannot be blank");
    }

    @Test
    @DisplayName("Should reject null carrier")
    void shouldRejectNullCarrier() {
        assertThatThrownBy(() -> resolver.resolveTrackingUrl(null, "1234567890"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Carrier cannot be null");
    }
}
