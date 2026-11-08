package com.shvmpk.stock_ledger.unit.shipping;

import com.shvmpk.stock_ledger.common.error.ShippingPriceNotConfiguredException;
import com.shvmpk.stock_ledger.shipping.entity.Carrier;
import com.shvmpk.stock_ledger.shipping.entity.ParcelSize;
import com.shvmpk.stock_ledger.shipping.entity.ShippingPriceRate;
import com.shvmpk.stock_ledger.shipping.repository.ShippingPriceListRepository;
import com.shvmpk.stock_ledger.shipping.service.ShippingPricingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShippingPricingService Unit Tests")
class ShippingPricingServiceTest {

    @Mock
    private ShippingPriceListRepository priceListRepository;

    @InjectMocks
    private ShippingPricingService pricingService;

    @Test
    @DisplayName("Should resolve shipping cost when active rate exists")
    void shouldResolveShippingCostWhenRateExists() {
        ShippingPriceRate rate = ShippingPriceRate.create(
                UUID.randomUUID(),
                Carrier.DHL,
                ParcelSize.MEDIUM,
                new BigDecimal("6.50")
        );

        when(priceListRepository.findByCarrierAndParcelSizeAndActiveTrue(Carrier.DHL, ParcelSize.MEDIUM))
                .thenReturn(Optional.of(rate));

        BigDecimal cost = pricingService.resolveShippingCost(Carrier.DHL, ParcelSize.MEDIUM);

        assertThat(cost).isEqualByComparingTo("6.50");
    }

    @Test
    @DisplayName("Should throw ShippingPriceNotConfiguredException when rate does not exist")
    void shouldThrowExceptionWhenRateNotFound() {
        when(priceListRepository.findByCarrierAndParcelSizeAndActiveTrue(Carrier.FEDEX, ParcelSize.LARGE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> pricingService.resolveShippingCost(Carrier.FEDEX, ParcelSize.LARGE))
                .isInstanceOf(ShippingPriceNotConfiguredException.class)
                .hasMessageContaining("No active shipping rate is configured for carrier FEDEX and parcel size LARGE.")
                .extracting(ex -> ((ShippingPriceNotConfiguredException) ex).getErrorCode())
                .isEqualTo("SHIPPING_PRICE_NOT_CONFIGURED");
    }
}
