package com.shvmpk.stock_ledger.inventory.entity;

import com.shvmpk.stock_ledger.product.entity.ProductVariant;
import com.shvmpk.stock_ledger.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "stock_movements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockMovement {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_variant_id", nullable = false)
    private ProductVariant productVariant;

    @Column(nullable = false, length = 100)
    private String sku;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(name = "delta_on_hand", nullable = false)
    private int deltaOnHand;

    @Column(name = "delta_reserved", nullable = false)
    private int deltaReserved;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private StockMovementReason reason;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "actor_id")
    private UUID actorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", insertable = false, updatable = false)
    private User actor;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static StockMovement create(
            ProductVariant variant,
            String sku,
            String productName,
            int deltaOnHand,
            int deltaReserved,
            StockMovementReason reason,
            String referenceType,
            UUID referenceId,
            UUID actorId,
            String notes
    ) {
        StockMovement movement = new StockMovement();
        movement.id = UUID.randomUUID();
        movement.productVariant = Objects.requireNonNull(variant, "Variant cannot be null");
        movement.sku = Objects.requireNonNull(sku, "SKU cannot be null");
        movement.productName = Objects.requireNonNull(productName, "Product name cannot be null");
        movement.deltaOnHand = deltaOnHand;
        movement.deltaReserved = deltaReserved;
        movement.reason = Objects.requireNonNull(reason, "Reason cannot be null");
        movement.referenceType = referenceType;
        movement.referenceId = referenceId;
        movement.actorId = actorId;
        movement.notes = notes;
        movement.createdAt = OffsetDateTime.now();
        return movement;
    }
}
