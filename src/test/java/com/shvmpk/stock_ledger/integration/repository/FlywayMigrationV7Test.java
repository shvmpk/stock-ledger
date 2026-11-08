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
@DisplayName("Flyway Migration V7 Test (Append-only Auth Event Audit Trail)")
class FlywayMigrationV7Test {

    private static final String ACTOR_EMAIL = "ingrid.bergstrom@nordicliving.se";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/141.0 Safari/537.36";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Creates auth_events with the expected column types and nullability")
    void shouldCreateAuthEventsTableWithExpectedColumns() {
        Map<String, String> columnTypes = jdbcTemplate.query("""
                SELECT column_name, data_type || ':' || is_nullable AS signature
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'auth_events'
                """, rs -> {
            Map<String, String> columns = new java.util.HashMap<>();
            while (rs.next()) {
                columns.put(rs.getString("column_name"), rs.getString("signature"));
            }
            return columns;
        });

        assertThat(columnTypes).containsEntry("id", "uuid:NO");
        assertThat(columnTypes).containsEntry("event_type", "character varying:NO");
        assertThat(columnTypes).containsEntry("failure_reason", "character varying:YES");
        assertThat(columnTypes).containsEntry("actor_user_id", "uuid:YES");
        assertThat(columnTypes).containsEntry("actor_email", "character varying:YES");
        assertThat(columnTypes).containsEntry("subject_user_id", "uuid:YES");
        assertThat(columnTypes).containsEntry("subject_email", "character varying:YES");
        assertThat(columnTypes).containsEntry("ip_address", "inet:YES");
        assertThat(columnTypes).containsEntry("user_agent", "character varying:YES");
        assertThat(columnTypes).containsEntry("request_id", "character varying:YES");
        assertThat(columnTypes).containsEntry("details", "jsonb:NO");
        assertThat(columnTypes).containsEntry("occurred_at", "timestamp with time zone:NO");
    }

    @Test
    @DisplayName("Rejects unknown event types")
    void shouldRejectUnknownEventTypes() {
        assertThatThrownBy(() -> insertEvent(UUID.randomUUID(), "SUPERADMIN_OVERRIDE", null))
                .as("Unknown event types must violate the check constraint")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Rejects unknown failure reasons")
    void shouldRejectUnknownFailureReasons() {
        assertThatThrownBy(() -> insertEvent(UUID.randomUUID(), "LOGIN_FAILED", "SHOULD_NOT_HAPPEN"))
                .as("Unknown failure reasons must violate the check constraint")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Blocks UPDATE on auth_events")
    void shouldRejectUpdatesToAuthEvents() {
        UUID id = UUID.randomUUID();
        insertEvent(id, "LOGIN_SUCCEEDED", null);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                UPDATE auth_events SET event_type = 'LOGOUT' WHERE id = ?
                """, id))
                .as("Audit rows must be immutable")
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    @DisplayName("Blocks DELETE on auth_events")
    void shouldRejectDeletesFromAuthEvents() {
        UUID id = UUID.randomUUID();
        insertEvent(id, "LOGOUT", null);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                DELETE FROM auth_events WHERE id = ?
                """, id))
                .as("Audit rows must never be deleted through the application role")
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    @DisplayName("Allows TRUNCATE as the documented test and break-glass escape hatch")
    void shouldAllowTruncate() {
        insertEvent(UUID.randomUUID(), "LOGIN_FAILED", "INVALID_PASSWORD");

        jdbcTemplate.execute("TRUNCATE auth_events");

        Integer remaining = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM auth_events", Integer.class);
        assertThat(remaining).isZero();
    }

    @Test
    @DisplayName("Creates indexes for the owner audit queries")
    void shouldCreateQueryIndexes() {
        List<String> indexes = jdbcTemplate.queryForList("""
                SELECT indexname FROM pg_indexes
                WHERE schemaname = 'public' AND tablename = 'auth_events'
                """, String.class);

        assertThat(indexes).contains(
                "idx_auth_events_occurred_at",
                "idx_auth_events_type_occurred_at",
                "idx_auth_events_actor_user",
                "idx_auth_events_subject_user",
                "idx_auth_events_actor_email_lower"
        );
    }

    @Test
    @DisplayName("Applies details and occurred_at defaults when omitted")
    void shouldApplyDefaults() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO auth_events (id, event_type, actor_email, ip_address, user_agent)
                VALUES (?, 'LOGIN_SUCCEEDED', ?, '192.0.2.14'::inet, ?)
                """, id, ACTOR_EMAIL, USER_AGENT);

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT details::text AS details, occurred_at
                FROM auth_events WHERE id = ?
                """, id);

        assertThat(row.get("details")).isEqualTo("{}");
        assertThat(row.get("occurred_at")).isNotNull();
    }

    private void insertEvent(UUID id, String eventType, String failureReason) {
        jdbcTemplate.update("""
                INSERT INTO auth_events (id, event_type, failure_reason, actor_email, ip_address, user_agent)
                VALUES (?, ?, ?, ?, '192.0.2.14'::inet, ?)
                """, id, eventType, failureReason, ACTOR_EMAIL, USER_AGENT);
    }
}
