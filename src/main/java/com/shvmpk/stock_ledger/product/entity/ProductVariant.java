package com.shvmpk.stock_ledger.product.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "product_variants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductVariant {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, unique = true, length = 100)
    private String sku;

    @Column(length = 100)
    private String barcode;

    @Column(name = "option_name", length = 100)
    private String optionName;

    @Column(name = "option_value", length = 100)
    private String optionValue;

    @Column(name = "cost_price", precision = 10, scale = 2)
    private BigDecimal costPrice;

    @Column(name = "selling_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal sellingPrice;

    @Column(name = "weight_grams")
    private Integer weightGrams;

    @Column(name = "min_stock_threshold")
    private Integer minStockThreshold;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static ProductVariant create(
            UUID id,
            Product product,
            String sku,
            String barcode,
            String optionName,
            String optionValue,
            BigDecimal costPrice,
            BigDecimal sellingPrice,
            Integer weightGrams,
            Integer minStockThreshold
    ) {
        validate(sku, costPrice, sellingPrice, weightGrams, minStockThreshold);

        ProductVariant variant = new ProductVariant();
        variant.id = Objects.requireNonNullElseGet(id, UUID::randomUUID);
        variant.product = product;
        variant.sku = sku.trim().toUpperCase();
        variant.barcode = barcode != null ? barcode.trim() : null;
        variant.optionName = optionName != null ? optionName.trim() : null;
        variant.optionValue = optionValue != null ? optionValue.trim() : null;
        variant.costPrice = costPrice;
        variant.sellingPrice = sellingPrice;
        variant.weightGrams = weightGrams;
        variant.minStockThreshold = minStockThreshold;
        variant.active = true;
        variant.createdAt = OffsetDateTime.now();
        variant.updatedAt = variant.createdAt;
        return variant;
    }

    public void updateDetails(
            String sku,
            String barcode,
            String optionName,
            String optionValue,
            BigDecimal costPrice,
            BigDecimal sellingPrice,
            Integer weightGrams,
            Integer minStockThreshold
    ) {
        validate(sku, costPrice, sellingPrice, weightGrams, minStockThreshold);

        this.sku = sku.trim().toUpperCase();
        this.barcode = trimToNull(barcode);
        this.optionName = trimToNull(optionName);
        this.optionValue = trimToNull(optionValue);
        this.costPrice = costPrice;
        this.sellingPrice = sellingPrice;
        this.weightGrams = weightGrams;
        this.minStockThreshold = minStockThreshold;
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * A variant can only be ordered or received when both the variant and its
     * parent product are active. This keeps product-level retirement from
     * having to rewrite every variant flag.
     */
    public boolean isSellable() {
        return active && product.isActive();
    }

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

    public BigDecimal calculateGrossMarginPercentage() {
        if (costPrice == null || sellingPrice.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        BigDecimal profit = sellingPrice.subtract(costPrice);
        return profit.divide(sellingPrice, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static void validate(
            String sku,
            BigDecimal costPrice,
            BigDecimal sellingPrice,
            Integer weightGrams,
            Integer minStockThreshold
    ) {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("SKU cannot be blank");
        }
        if (costPrice != null && costPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Cost price cannot be negative");
        }
        if (sellingPrice == null || sellingPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Selling price cannot be negative");
        }
        if (weightGrams != null && weightGrams < 0) {
            throw new IllegalArgumentException("Weight cannot be negative");
        }
        if (minStockThreshold != null && minStockThreshold < 0) {
            throw new IllegalArgumentException("Min stock threshold cannot be negative");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
