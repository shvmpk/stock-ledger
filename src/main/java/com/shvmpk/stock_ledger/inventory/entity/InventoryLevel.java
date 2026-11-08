package com.shvmpk.stock_ledger.inventory.entity;

import com.shvmpk.stock_ledger.product.entity.ProductVariant;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "inventory_levels")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryLevel {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_variant_id", nullable = false, unique = true)
    private ProductVariant productVariant;

    @Column(name = "on_hand_quantity", nullable = false)
    private int onHandQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static InventoryLevel initialize(UUID id, ProductVariant variant, int initialOnHand) {
        if (initialOnHand < 0) {
            throw new IllegalArgumentException("Initial on-hand quantity cannot be negative");
        }
        InventoryLevel inventory = new InventoryLevel();
        inventory.id = Objects.requireNonNullElseGet(id, UUID::randomUUID);
        inventory.productVariant = Objects.requireNonNull(variant, "Product variant cannot be null");
        inventory.onHandQuantity = initialOnHand;
        inventory.reservedQuantity = 0;
        inventory.version = 0L;
        inventory.updatedAt = OffsetDateTime.now();
        return inventory;
    }

    public int getAvailableQuantity() {
        return this.onHandQuantity - this.reservedQuantity;
    }

    public boolean isLowStock() {
        Integer minStockThreshold = this.productVariant.getMinStockThreshold();
        return minStockThreshold != null && getAvailableQuantity() <= minStockThreshold;
    }
}
