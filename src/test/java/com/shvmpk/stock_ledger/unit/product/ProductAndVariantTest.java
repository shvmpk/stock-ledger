package com.shvmpk.stock_ledger.unit.product;

import com.shvmpk.stock_ledger.product.entity.Product;
import com.shvmpk.stock_ledger.product.entity.ProductVariant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Product & Variant Domain Unit Tests")
class ProductAndVariantTest {

    @Test
    @DisplayName("Should create Product with realistic attributes and add variants")
    void shouldCreateProductWithVariants() {
        Product product = Product.create(
                UUID.randomUUID(),
                "Heavyweight Cotton Hoodie",
                "Crafted from 400 GSM brushed organic fleece",
                "Apparel"
        );

        ProductVariant variantXl = product.addVariant(
                UUID.randomUUID(),
                "HOOD-NVY-XL",
                "5060123456789",
                "Size/Color",
                "Navy / XL",
                new BigDecimal("22.50"),
                new BigDecimal("60.00"),
                480,
                5
        );

        assertThat(product.getName()).isEqualTo("Heavyweight Cotton Hoodie");
        assertThat(product.getVariants()).hasSize(1);
        assertThat(variantXl.getSku()).isEqualTo("HOOD-NVY-XL");
        assertThat(variantXl.getBarcode()).isEqualTo("5060123456789");
        assertThat(variantXl.getCostPrice()).isEqualByComparingTo("22.50");
        assertThat(variantXl.getSellingPrice()).isEqualByComparingTo("60.00");
        assertThat(variantXl.getWeightGrams()).isEqualTo(480);
        assertThat(variantXl.getMinStockThreshold()).isEqualTo(5);
        assertThat(variantXl.isActive()).isTrue();
    }

    @Test
    @DisplayName("Should calculate gross margin correctly")
    void shouldCalculateGrossMargin() {
        Product product = Product.create(UUID.randomUUID(), "Specialty Coffee Roaster", null, "Equipment");
        ProductVariant variant = product.addVariant(
                UUID.randomUUID(),
                "EQUIP-ROAST-01",
                "5060123459999",
                "Standard",
                "Default",
                new BigDecimal("100.00"),
                new BigDecimal("250.00"),
                1500,
                2
        );

        // Margin = (250 - 100) / 250 = 60.00%
        BigDecimal margin = variant.calculateGrossMarginPercentage();
        assertThat(margin).isEqualByComparingTo("60.00");
    }

    @Test
    @DisplayName("Should update product details and trim surrounding whitespace")
    void shouldUpdateProductDetails() {
        Product product = Product.create(
                UUID.randomUUID(),
                "Heavyweight Cotton Hoodie",
                "480 gsm brushed-back cotton",
                "Apparel"
        );

        product.updateDetails(
                "  Heavyweight Cotton Hoodie 2026  ",
                "  Reissued with a heavier 500 gsm loopback fleece.  ",
                "  Apparel  "
        );

        assertThat(product.getName()).isEqualTo("Heavyweight Cotton Hoodie 2026");
        assertThat(product.getDescription())
                .isEqualTo("Reissued with a heavier 500 gsm loopback fleece.");
        assertThat(product.getCategory()).isEqualTo("Apparel");
    }

    @Test
    @DisplayName("Should allow an unknown cost, weight and reorder level")
    void shouldAllowUnknownOptionalValues() {
        Product product = Product.create(
                UUID.randomUUID(),
                "Linen Sample Swatch Set",
                null,
                "Homeware"
        );

        ProductVariant variant = product.addVariant(
                UUID.randomUUID(),
                "SWATCH-LIN-01",
                null,
                "Fabric",
                "Natural linen",
                null,
                new BigDecimal("14.00"),
                null,
                null
        );

        assertThat(variant.getCostPrice()).isNull();
        assertThat(variant.getWeightGrams()).isNull();
        assertThat(variant.getMinStockThreshold()).isNull();
        assertThat(variant.calculateGrossMarginPercentage()).isNull();
    }

    @Test
    @DisplayName("Should keep an explicit zero cost distinct from an unknown cost")
    void shouldKeepZeroCostDistinctFromUnknown() {
        Product product = Product.create(
                UUID.randomUUID(),
                "Linen Sample Swatch Set",
                null,
                "Homeware"
        );

        ProductVariant variant = product.addVariant(
                UUID.randomUUID(),
                "SWATCH-LIN-01",
                null,
                "Fabric",
                "Natural linen",
                BigDecimal.ZERO,
                new BigDecimal("14.00"),
                0,
                0
        );

        assertThat(variant.getCostPrice()).isEqualByComparingTo("0.00");
        assertThat(variant.getWeightGrams()).isZero();
        assertThat(variant.getMinStockThreshold()).isZero();
        assertThat(variant.calculateGrossMarginPercentage()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("Should reject a blank product name on update")
    void shouldRejectBlankProductNameOnUpdate() {
        Product product = Product.create(
                UUID.randomUUID(),
                "Heavyweight Cotton Hoodie",
                null,
                "Apparel"
        );

        assertThatThrownBy(() -> product.updateDetails("   ", null, "Apparel"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Product name cannot be blank");
    }

    @Test
    @DisplayName("Should update variant details and normalise the SKU")
    void shouldUpdateVariantDetails() {
        Product product = Product.create(
                UUID.randomUUID(),
                "Heavyweight Cotton Hoodie",
                null,
                "Apparel"
        );
        ProductVariant variant = product.addVariant(
                UUID.randomUUID(),
                "HOOD-NVY-XL",
                "5060123456789",
                "Size / Color",
                "XL / Navy",
                new BigDecimal("24.00"),
                new BigDecimal("58.00"),
                680,
                5
        );

        variant.updateDetails(
                "  hood-nvy-xl-26  ",
                "5060123456796",
                "Size / Color",
                "XXL / Navy",
                new BigDecimal("26.00"),
                new BigDecimal("62.00"),
                720,
                6
        );

        assertThat(variant.getSku()).isEqualTo("HOOD-NVY-XL-26");
        assertThat(variant.getBarcode()).isEqualTo("5060123456796");
        assertThat(variant.getOptionValue()).isEqualTo("XXL / Navy");
        assertThat(variant.getCostPrice()).isEqualByComparingTo("26.00");
        assertThat(variant.getSellingPrice()).isEqualByComparingTo("62.00");
        assertThat(variant.getWeightGrams()).isEqualTo(720);
        assertThat(variant.getMinStockThreshold()).isEqualTo(6);
    }

    @Test
    @DisplayName("Should reject negative values on variant update")
    void shouldRejectNegativeValuesOnVariantUpdate() {
        Product product = Product.create(
                UUID.randomUUID(),
                "Heavyweight Cotton Hoodie",
                null,
                "Apparel"
        );
        ProductVariant variant = product.addVariant(
                UUID.randomUUID(),
                "HOOD-NVY-XL",
                null,
                null,
                null,
                new BigDecimal("24.00"),
                new BigDecimal("58.00"),
                680,
                5
        );

        assertThatThrownBy(() -> variant.updateDetails(
                "HOOD-NVY-XL",
                null,
                null,
                null,
                new BigDecimal("-1.00"),
                new BigDecimal("58.00"),
                680,
                5
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cost price cannot be negative");

        assertThatThrownBy(() -> variant.updateDetails(
                "HOOD-NVY-XL",
                null,
                null,
                null,
                new BigDecimal("24.00"),
                new BigDecimal("58.00"),
                -1,
                5
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Weight cannot be negative");
    }

    @Test
    @DisplayName("Should reject invalid SKU or negative prices")
    void shouldRejectInvalidVariantData() {
        Product product = Product.create(UUID.randomUUID(), "T-Shirt", null, "Apparel");

        assertThatThrownBy(() -> product.addVariant(
                UUID.randomUUID(),
                "   ",
                null,
                null,
                null,
                BigDecimal.TEN,
                BigDecimal.valueOf(20),
                100,
                5
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("SKU cannot be blank");

        assertThatThrownBy(() -> product.addVariant(
                UUID.randomUUID(),
                "SKU-1",
                null,
                null,
                null,
                BigDecimal.valueOf(-5),
                BigDecimal.valueOf(20),
                100,
                5
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Cost price cannot be negative");
    }
}
