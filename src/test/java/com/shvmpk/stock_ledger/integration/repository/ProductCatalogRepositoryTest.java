package com.shvmpk.stock_ledger.integration.repository;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import com.shvmpk.stock_ledger.product.entity.Product;
import com.shvmpk.stock_ledger.product.entity.ProductVariant;
import com.shvmpk.stock_ledger.product.repository.ProductRepository;
import com.shvmpk.stock_ledger.product.repository.ProductVariantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
@DisplayName("Product and ProductVariant Repository Integration Tests")
class ProductCatalogRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Test
    @DisplayName("Should persist parent Product and cascade variants, finding variant by SKU")
    void shouldPersistProductWithVariantsAndFindBySku() {
        Product product = Product.create(
                UUID.randomUUID(),
                "Organic Merino Wool Beanie",
                "100% fine merino wool, ribbed knit",
                "Accessories"
        );

        product.addVariant(
                UUID.randomUUID(),
                "BEANIE-CHAR-OS",
                "5060123490001",
                "Color",
                "Charcoal",
                new BigDecimal("12.00"),
                new BigDecimal("32.00"),
                110,
                5
        );

        productRepository.save(product);

        Optional<ProductVariant> found = productVariantRepository.findBySkuIgnoreCase("beanie-char-os");
        assertThat(found).isPresent();
        assertThat(found.get().getSku()).isEqualTo("BEANIE-CHAR-OS");
        assertThat(found.get().getProduct().getName()).isEqualTo("Organic Merino Wool Beanie");
        assertThat(found.get().getCostPrice()).isEqualByComparingTo("12.00");
    }
}
