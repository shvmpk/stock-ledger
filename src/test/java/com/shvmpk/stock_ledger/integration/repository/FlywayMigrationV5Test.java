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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
@DisplayName("Flyway Migration V5 Test (Backend Alignment & Platform Credentials)")
class FlywayMigrationV5Test {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Renames the webhook secret to ciphertext and adds deactivation metadata")
    void shouldRenameWebhookSecretAndAddDeactivation() {
        Integer oldColumnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_name = 'platform_connections' AND column_name = 'webhook_secret_hash'
                """, Integer.class);
        assertThat(oldColumnCount).isZero();

        String dataType = jdbcTemplate.queryForObject("""
                SELECT data_type FROM information_schema.columns
                WHERE table_name = 'platform_connections' AND column_name = 'webhook_secret_encrypted'
                """, String.class);
        assertThat(dataType).isEqualTo("text");

        Integer deactivatedAtCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_name = 'platform_connections' AND column_name = 'deactivated_at'
                  AND data_type = 'timestamp with time zone'
                """, Integer.class);
        assertThat(deactivatedAtCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Adds the stock count item unique constraint and persisted deficit columns")
    void shouldAddStockCountItemConstraintsAndDeficits() {
        Integer uniqueConstraint = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE table_name = 'stock_count_items'
                  AND constraint_name = 'uq_stock_count_items_session_variant'
                  AND constraint_type = 'UNIQUE'
                """, Integer.class);
        assertThat(uniqueConstraint).isEqualTo(1);

        assertColumn("stock_count_items", "has_reservation_deficit", "boolean", "NO");
        assertColumn("stock_count_items", "reservation_deficit", "integer", "NO");
    }

    @Test
    @DisplayName("Adds nullable operator notes to returns")
    void shouldAddReturnOperatorNotes() {
        assertColumn("order_returns", "operator_notes", "text", "YES");
    }

    @Test
    @DisplayName("Adds the order queue index")
    void shouldAddOrderQueueIndex() {
        Integer indexCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE tablename = 'orders' AND indexname = 'idx_orders_status_created_at'
                """, Integer.class);
        assertThat(indexCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Allows PENDING sync status and rejects an unknown status")
    void shouldAllowPendingSyncStatusAndRejectUnknown() {
        insertInactivePlatform("SHOPIFY", "PENDING");

        assertThatThrownBy(() -> insertInactivePlatform("SHOPIFY", "NOT_A_STATUS"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Allows active connections of different types and multiple inactive rows of one type")
    void shouldAllowDifferentActiveTypesAndMultipleInactiveRows() {
        insertActivePlatform("SHOPIFY");
        insertActivePlatform("WOOCOMMERCE");
        insertInactivePlatform("SHOPIFY", "SUCCESS");
        insertInactivePlatform("SHOPIFY", "FAILED");

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM platform_connections WHERE platform_type = 'SHOPIFY'",
                Integer.class
        );
        assertThat(count).isEqualTo(3);
    }

    private void assertColumn(String table, String column, String dataType, String nullable) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_name = ? AND column_name = ? AND data_type = ? AND is_nullable = ?
                """, Integer.class, table, column, dataType, nullable);
        assertThat(count).isEqualTo(1);
    }

    private void insertActivePlatform(String platformType) {
        jdbcTemplate.update("""
                INSERT INTO platform_connections
                    (id, platform_type, api_endpoint, webhook_secret_encrypted, sync_status, active)
                VALUES (?, ?, ?, ?, 'SUCCESS', TRUE)
                """, UUID.randomUUID(), platformType,
                "https://northwind-supply.myshopify.com", "");
    }

    private void insertInactivePlatform(String platformType, String syncStatus) {
        jdbcTemplate.update("""
                INSERT INTO platform_connections
                    (id, platform_type, api_endpoint, webhook_secret_encrypted, sync_status, active)
                VALUES (?, ?, ?, ?, ?, FALSE)
                """, UUID.randomUUID(), platformType,
                "https://northwind-supply.myshopify.com", "", syncStatus);
    }
}
