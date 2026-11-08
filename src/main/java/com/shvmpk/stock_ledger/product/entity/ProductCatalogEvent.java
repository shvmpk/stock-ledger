package com.shvmpk.stock_ledger.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable history of a catalogue retirement or restoration. The product name
 * and variant SKU are snapshotted rather than joined, so the record still reads
 * correctly after the catalogue is edited again. The database also enforces
 * append-only semantics with a trigger.
 */
@Entity
@Table(name = "product_catalog_events")
@Immutable
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductCatalogEvent {

    @Id
    private UUID id;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "variant_id", updatable = false)
    private UUID variantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 20, updatable = false)
    private ProductCatalogScope scope;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 20, updatable = false)
    private ProductCatalogChangeType changeType;

    @Column(name = "product_name", nullable = false, length = 255, updatable = false)
    private String productName;

    @Column(name = "variant_sku", length = 100, updatable = false)
    private String variantSku;

    @Column(name = "actor_user_id", updatable = false)
    private UUID actorUserId;

    @Column(name = "actor_email", length = 255, updatable = false)
    private String actorEmail;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private OffsetDateTime occurredAt;

    public static ProductCatalogEvent record(
            Product product,
            ProductVariant variant,
            ProductCatalogChangeType changeType,
            UUID actorUserId,
            String actorEmail
    ) {
        Objects.requireNonNull(product, "product");
        Objects.requireNonNull(changeType, "changeType");

        return new ProductCatalogEvent(
                UUID.randomUUID(),
                product.getId(),
                variant == null ? null : variant.getId(),
                variant == null ? ProductCatalogScope.PRODUCT : ProductCatalogScope.VARIANT,
                changeType,
                product.getName(),
                variant == null ? null : variant.getSku(),
                actorUserId,
                actorEmail,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }
}
