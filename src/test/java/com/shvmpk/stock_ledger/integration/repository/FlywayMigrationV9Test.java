package com.shvmpk.stock_ledger.integration.repository;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
@DisplayName("Flyway Migration V9 Test (Multiple Stores per Platform)")
class FlywayMigrationV9Test {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Replaces per-type uniqueness with webhook address uniqueness")
    void shouldReplaceTypeUniquenessWithWebhookAddressUniqueness() {
        List<String> indexes = jdbcTemplate.queryForList("""
                SELECT indexname FROM pg_indexes
                WHERE schemaname = 'public' AND tablename = 'platform_connections'
                """, String.class);

        assertThat(indexes)
                .doesNotContain("uq_platform_connections_active_type")
                .contains("uq_platform_connections_active_slug");
    }

    @Test
    @DisplayName("Allows two active Shopify stores with different webhook addresses")
    void shouldAllowTwoActiveStoresOfTheSamePlatform() {
        insertActiveShopify("Northwind Supply", "northwind-supply");
        insertActiveShopify("Harbour & Pine", "harbour-pine");

        Integer activeShopify = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM platform_connections
                WHERE platform_type = 'SHOPIFY' AND active = TRUE
                """, Integer.class);

        assertThat(activeShopify).isEqualTo(2);
    }

    @Test
    @DisplayName("Rejects two active connections that share one webhook address")
    void shouldRejectTwoActiveConnectionsSharingOneWebhookAddress() {
        insertActiveShopify("Northwind Supply", "northwind-supply");

        assertThatThrownBy(() -> insertActiveShopify("Northwind Supply (duplicate)", "northwind-supply"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertActiveShopify(String displayName, String slug) {
        jdbcTemplate.update("""
                INSERT INTO platform_connections
                    (id, platform_type, display_name, slug, api_endpoint,
                     webhook_secret_encrypted, sync_status, active)
                VALUES (?, 'SHOPIFY', ?, ?, ?, 'encrypted-secret', 'PENDING', TRUE)
                """, UUID.randomUUID(), displayName, slug,
                "https://" + slug + ".myshopify.com");
    }
}
