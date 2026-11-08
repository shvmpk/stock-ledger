package com.shvmpk.stock_ledger.integration.repository;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import com.shvmpk.stock_ledger.product.entity.Product;
import com.shvmpk.stock_ledger.product.entity.ProductCatalogChangeType;
import com.shvmpk.stock_ledger.product.entity.ProductCatalogEvent;
import com.shvmpk.stock_ledger.product.entity.ProductCatalogScope;
import com.shvmpk.stock_ledger.product.entity.ProductVariant;
import com.shvmpk.stock_ledger.product.repository.ProductCatalogEventRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
@DisplayName("Product catalogue event repository")
class ProductCatalogEventRepositoryTest {

    private static final UUID ACTOR_ID =
            UUID.fromString("2f4a6c8e-0b1d-4f3a-9c5e-7a9b1d3f5c7e");
    private static final String ACTOR_EMAIL = "elin.sandberg@northwind-supply.co";

    @Autowired
    private ProductCatalogEventRepository eventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE product_catalog_events");
    }

    @Test
    @DisplayName("Persists product and variant snapshots and reads them back")
    void shouldPersistCatalogueSnapshots() {
        Product hoodie = Product.create(
                UUID.randomUUID(),
                "Heavyweight Cotton Hoodie",
                "480 gsm brushed-back cotton hoodie with a double-layer hood.",
                "Apparel"
        );
        ProductVariant hoodieXl = hoodie.addVariant(
                UUID.randomUUID(),
                "HOOD-NVY-XL",
                "5060123456004",
                "Size",
                "XL",
                new BigDecimal("24.00"),
                new BigDecimal("58.00"),
                680,
                5
        );

        ProductCatalogEvent event = ProductCatalogEvent.record(
                hoodie,
                hoodieXl,
                ProductCatalogChangeType.RETIRED,
                ACTOR_ID,
                ACTOR_EMAIL
        );

        eventRepository.saveAndFlush(event);
        entityManager.clear();

        ProductCatalogEvent reloaded = eventRepository.findById(event.getId()).orElseThrow();
        assertThat(reloaded.getProductId()).isEqualTo(hoodie.getId());
        assertThat(reloaded.getVariantId()).isEqualTo(hoodieXl.getId());
        assertThat(reloaded.getScope()).isEqualTo(ProductCatalogScope.VARIANT);
        assertThat(reloaded.getChangeType()).isEqualTo(ProductCatalogChangeType.RETIRED);
        assertThat(reloaded.getProductName()).isEqualTo("Heavyweight Cotton Hoodie");
        assertThat(reloaded.getVariantSku()).isEqualTo("HOOD-NVY-XL");
        assertThat(reloaded.getActorUserId()).isEqualTo(ACTOR_ID);
        assertThat(reloaded.getActorEmail()).isEqualTo(ACTOR_EMAIL);
        assertThat(reloaded.getOccurredAt()).isNotNull();
    }

    @Test
    @DisplayName("The database trigger refuses updates to catalogue history")
    void shouldRefuseUpdates() {
        UUID eventId = insertEvent();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE product_catalog_events SET product_name = 'Rewritten' WHERE id = ?",
                eventId
        ))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    @DisplayName("The database trigger refuses deletes from catalogue history")
    void shouldRefuseDeletes() {
        UUID eventId = insertEvent();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM product_catalog_events WHERE id = ?",
                eventId
        ))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    private UUID insertEvent() {
        UUID eventId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO product_catalog_events
                    (id, product_id, variant_id, scope, change_type,
                     product_name, variant_sku, actor_user_id, actor_email)
                VALUES (?, ?, ?, 'VARIANT', 'RETIRED',
                        'Heavyweight Cotton Hoodie', 'HOOD-NVY-XL', ?, ?)
                """,
                eventId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                ACTOR_ID,
                ACTOR_EMAIL
        );
        return eventId;
    }
}
