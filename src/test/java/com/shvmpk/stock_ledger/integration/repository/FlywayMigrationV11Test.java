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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
@DisplayName("Flyway Migration V11 Test (Safe Platform Connection Changes)")
class FlywayMigrationV11Test {

    private static final String SHOPIFY_ENDPOINT = "https://northwind-supply.myshopify.com";
    private static final String CIPHERTEXT = "v1:9f4c2a7b1e8d6350:7c1f4b2e9a6d8350";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Adds an optimistic-locking version and an optional creation request key")
    void shouldAddVersionAndCreationRequestKeyColumns() {
        Map<String, String> columns = jdbcTemplate.query("""
                SELECT column_name, data_type || ':' || is_nullable AS signature
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'platform_connections'
                """, rs -> {
            Map<String, String> found = new HashMap<>();
            while (rs.next()) {
                found.put(rs.getString("column_name"), rs.getString("signature"));
            }
            return found;
        });

        assertThat(columns).containsEntry("version", "bigint:NO");
        assertThat(columns).containsEntry("creation_request_key", "character varying:YES");
    }

    @Test
    @DisplayName("Gives every existing connection version zero")
    void shouldBackfillVersionZero() {
        UUID id = UUID.randomUUID();
        insertConnection(id, null);

        Long version = jdbcTemplate.queryForObject(
                "SELECT version FROM platform_connections WHERE id = ?",
                Long.class,
                id
        );

        assertThat(version).isZero();
    }

    @Test
    @DisplayName("Allows several connections without a creation request key")
    void shouldAllowSeveralNullCreationRequestKeys() {
        insertConnection(UUID.randomUUID(), null);
        insertConnection(UUID.randomUUID(), null);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM platform_connections WHERE creation_request_key IS NULL",
                Integer.class
        );

        assertThat(count).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Rejects two connections created by the same request key")
    void shouldRejectDuplicateCreationRequestKeys() {
        String requestKey = "0f7a5c3e-9b1d-4a6f-8c2e-5d4b3a2f1e90";
        insertConnection(UUID.randomUUID(), requestKey);

        assertThatThrownBy(() -> insertConnection(UUID.randomUUID(), requestKey))
                .as("One submitted intent may create at most one connection")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Indexes the creation request key uniquely among non-null values")
    void shouldIndexCreationRequestKey() {
        List<String> indexes = jdbcTemplate.queryForList("""
                SELECT indexname FROM pg_indexes
                WHERE schemaname = 'public' AND tablename = 'platform_connections'
                """, String.class);

        assertThat(indexes).contains("uq_platform_connections_creation_request_key");
    }

    private void insertConnection(UUID id, String creationRequestKey) {
        jdbcTemplate.update("""
                INSERT INTO platform_connections
                    (id, platform_type, display_name, slug, api_endpoint,
                     webhook_secret_encrypted, sync_status, active,
                     created_at, updated_at, creation_request_key)
                VALUES (?, 'SHOPIFY', 'Northwind Supply', ?, ?, ?, 'PENDING', TRUE,
                        NOW(), NOW(), ?)
                """, id, "northwind-supply-" + id, SHOPIFY_ENDPOINT, CIPHERTEXT, creationRequestKey);
    }
}
