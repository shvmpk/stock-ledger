package com.shvmpk.stock_ledger.integration.repository;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
@DisplayName("Flyway Migration V10 Test (Append-only Platform Connection History)")
class FlywayMigrationV10Test {

    private static final UUID CONNECTION_ID = UUID.fromString("6c1d2e3f-4a5b-4c6d-8e9f-0a1b2c3d4e5f");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Creates the history table with the expected column types and nullability")
    void shouldCreateHistoryTableWithExpectedColumns() {
        Map<String, String> columnTypes = jdbcTemplate.query("""
                SELECT column_name, data_type || ':' || is_nullable AS signature
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'platform_connection_events'
                """, rs -> {
            Map<String, String> columns = new java.util.HashMap<>();
            while (rs.next()) {
                columns.put(rs.getString("column_name"), rs.getString("signature"));
            }
            return columns;
        });

        assertThat(columnTypes).containsEntry("id", "uuid:NO");
        assertThat(columnTypes).containsEntry("platform_connection_id", "uuid:NO");
        assertThat(columnTypes).containsEntry("platform_type", "character varying:NO");
        assertThat(columnTypes).containsEntry("change_type", "character varying:NO");
        assertThat(columnTypes).containsEntry("actor_user_id", "uuid:YES");
        assertThat(columnTypes).containsEntry("actor_email", "character varying:YES");
        assertThat(columnTypes).containsEntry("fields", "jsonb:NO");
        assertThat(columnTypes).containsEntry("occurred_at", "timestamp with time zone:NO");
    }

    @Test
    @DisplayName("Rejects unknown change types")
    void shouldRejectUnknownChangeTypes() {
        assertThatThrownBy(() -> insertEvent(UUID.randomUUID(), "TELEPORTED"))
                .as("Only the four supported change kinds may be recorded")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Blocks UPDATE on the history table")
    void shouldRejectUpdates() {
        UUID id = UUID.randomUUID();
        insertEvent(id, "CREATED");

        assertThatThrownBy(() -> jdbcTemplate.update("""
                UPDATE platform_connection_events SET change_type = 'PAUSED' WHERE id = ?
                """, id))
                .as("History rows must be immutable")
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    @DisplayName("Blocks DELETE on the history table")
    void shouldRejectDeletes() {
        UUID id = UUID.randomUUID();
        insertEvent(id, "CREATED");

        assertThatThrownBy(() -> jdbcTemplate.update("""
                DELETE FROM platform_connection_events WHERE id = ?
                """, id))
                .as("History rows must never be deleted through the application role")
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    @DisplayName("Allows TRUNCATE as the documented test and break-glass escape hatch")
    void shouldAllowTruncate() {
        insertEvent(UUID.randomUUID(), "CREATED");

        jdbcTemplate.execute("TRUNCATE platform_connection_events");

        Integer remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM platform_connection_events",
                Integer.class
        );
        assertThat(remaining).isZero();
    }

    @Test
    @DisplayName("Indexes history by connection and time")
    void shouldIndexHistoryByConnectionAndTime() {
        List<String> indexes = jdbcTemplate.queryForList("""
                SELECT indexname FROM pg_indexes
                WHERE schemaname = 'public' AND tablename = 'platform_connection_events'
                """, String.class);

        assertThat(indexes).contains("idx_platform_connection_events_connection_occurred_at");
    }

    @Test
    @DisplayName("Defaults the affected fields to an empty list and stamps the time")
    void shouldApplyDefaults() {
        UUID id = UUID.randomUUID();
        insertEvent(id, "PAUSED");

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT fields::text AS fields, occurred_at
                FROM platform_connection_events WHERE id = ?
                """, id);

        assertThat(row.get("fields")).isEqualTo("[]");
        assertThat(row.get("occurred_at")).isNotNull();
    }

    private void insertEvent(UUID id, String changeType) {
        jdbcTemplate.update("""
                INSERT INTO platform_connection_events
                    (id, platform_connection_id, platform_type, change_type, actor_email)
                VALUES (?, ?, 'SHOPIFY', ?, 'elin.sandberg@northwind-supply.co')
                """, id, CONNECTION_ID, changeType);
    }
}
