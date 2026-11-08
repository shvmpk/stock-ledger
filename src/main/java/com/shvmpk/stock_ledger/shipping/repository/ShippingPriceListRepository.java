package com.shvmpk.stock_ledger.shipping.repository;

import com.shvmpk.stock_ledger.shipping.entity.Carrier;
import com.shvmpk.stock_ledger.shipping.entity.ParcelSize;
import com.shvmpk.stock_ledger.shipping.entity.ShippingPriceRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShippingPriceListRepository extends JpaRepository<ShippingPriceRate, UUID> {

    Optional<ShippingPriceRate> findByCarrierAndParcelSizeAndActiveTrue(Carrier carrier, ParcelSize parcelSize);

    Optional<ShippingPriceRate> findByCarrierAndParcelSize(Carrier carrier, ParcelSize parcelSize);
}
