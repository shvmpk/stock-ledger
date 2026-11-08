package com.shvmpk.stock_ledger.product.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 100)
    private String category;

    @Column(nullable = false)
    private boolean active;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductVariant> variants = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static Product create(UUID id, String name, String description, String category) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Product name cannot be blank");
        }
        Product product = new Product();
        product.id = Objects.requireNonNullElseGet(id, UUID::randomUUID);
        product.name = name.trim();
        product.description = description != null ? description.trim() : null;
        product.category = category != null ? category.trim() : null;
        product.active = true;
        product.createdAt = OffsetDateTime.now();
        product.updatedAt = product.createdAt;
        return product;
    }

    public void updateDetails(String name, String description, String category) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Product name cannot be blank");
        }
        this.name = name.trim();
        this.description = trimToNull(description);
        this.category = trimToNull(category);
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * Retiring keeps the row and all of its history. Order intake and stock
     * receiving consult {@link ProductVariant#isSellable()}, so a retired
     * product blocks every variant without rewriting their individual flags.
     */
    public void retire() {
        if (active) {
            active = false;
            updatedAt = OffsetDateTime.now();
        }
    }

    public void restore() {
        if (!active) {
            active = true;
            updatedAt = OffsetDateTime.now();
        }
    }

    public ProductVariant addVariant(
            UUID variantId,
            String sku,
            String barcode,
            String optionName,
            String optionValue,
            BigDecimal costPrice,
            BigDecimal sellingPrice,
            Integer weightGrams,
            Integer minStockThreshold
    ) {
        ProductVariant variant = ProductVariant.create(
                variantId,
                this,
                sku,
                barcode,
                optionName,
                optionValue,
                costPrice,
                sellingPrice,
                weightGrams,
                minStockThreshold
        );
        this.variants.add(variant);
        return variant;
    }

    public List<ProductVariant> getVariants() {
        return Collections.unmodifiableList(this.variants);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
