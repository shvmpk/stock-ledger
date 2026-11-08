package com.shvmpk.stock_ledger.shipping.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "shipping_price_list")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShippingPriceRate {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Carrier carrier;

    @Enumerated(EnumType.STRING)
    @Column(name = "parcel_size", nullable = false, length = 50)
    private ParcelSize parcelSize;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal cost;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static ShippingPriceRate create(UUID id, Carrier carrier, ParcelSize parcelSize, BigDecimal cost) {
        ShippingPriceRate rate = new ShippingPriceRate();
        rate.id = Objects.requireNonNullElseGet(id, UUID::randomUUID);
        rate.carrier = Objects.requireNonNull(carrier, "Carrier cannot be null");
        rate.parcelSize = Objects.requireNonNull(parcelSize, "ParcelSize cannot be null");
        rate.cost = Objects.requireNonNull(cost, "Cost cannot be null");
        rate.active = true;
        rate.updatedAt = OffsetDateTime.now();
        return rate;
    }

    public void update(BigDecimal cost, boolean active) {
        this.cost = Objects.requireNonNull(cost, "Cost cannot be null");
        this.active = active;
        this.updatedAt = OffsetDateTime.now();
    }
}
