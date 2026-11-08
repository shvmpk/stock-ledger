package com.shvmpk.stock_ledger.shipping.service;

import com.shvmpk.stock_ledger.common.error.ShippingPriceNotConfiguredException;
import com.shvmpk.stock_ledger.shipping.dto.ShippingRateDto;
import com.shvmpk.stock_ledger.shipping.dto.UpdateShippingRateRequest;
import com.shvmpk.stock_ledger.shipping.entity.Carrier;
import com.shvmpk.stock_ledger.shipping.entity.ParcelSize;
import com.shvmpk.stock_ledger.shipping.entity.ShippingPriceRate;
import com.shvmpk.stock_ledger.shipping.repository.ShippingPriceListRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShippingPricingService {

    private final ShippingPriceListRepository priceListRepository;

    public BigDecimal resolveShippingCost(Carrier carrier, ParcelSize parcelSize) {
        return priceListRepository.findByCarrierAndParcelSizeAndActiveTrue(carrier, parcelSize)
                .map(ShippingPriceRate::getCost)
                .orElseThrow(() -> new ShippingPriceNotConfiguredException(carrier.name(), parcelSize.name()));
    }

    @Transactional(readOnly = true)
    public List<ShippingRateDto> getAllRates() {
        return priceListRepository.findAll().stream()
                .map(r -> new ShippingRateDto(r.getId(), r.getCarrier(), r.getParcelSize(), r.getCost(), r.isActive()))
                .toList();
    }

    @Transactional
    public ShippingRateDto configureRate(UpdateShippingRateRequest request) {
        ShippingPriceRate rate = priceListRepository.findByCarrierAndParcelSize(request.carrier(), request.parcelSize())
                .orElseGet(() -> ShippingPriceRate.create(UUID.randomUUID(), request.carrier(), request.parcelSize(), request.cost()));

        rate.update(request.cost(), request.active());
        ShippingPriceRate saved = priceListRepository.save(rate);
        return new ShippingRateDto(saved.getId(), saved.getCarrier(), saved.getParcelSize(), saved.getCost(), saved.isActive());
    }
}
