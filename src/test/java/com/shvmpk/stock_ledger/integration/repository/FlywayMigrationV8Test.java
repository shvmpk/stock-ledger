package com.shvmpk.stock_ledger.integration.repository;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
@DisplayName("Flyway Migration V8 Test (Password Lifecycle Columns)")
class FlywayMigrationV8Test {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Adds must_change_password and password_changed_at with the expected nullability")
    void shouldAddPasswordLifecycleColumns() {
        Map<String, String> columnTypes = jdbcTemplate.query("""
                SELECT column_name, data_type || ':' || is_nullable AS signature
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'users'
                """, rs -> {
            Map<String, String> columns = new java.util.HashMap<>();
            while (rs.next()) {
                columns.put(rs.getString("column_name"), rs.getString("signature"));
            }
            return columns;
        });

        assertThat(columnTypes).containsEntry("must_change_password", "boolean:NO");
        assertThat(columnTypes).containsEntry("password_changed_at", "timestamp with time zone:NO");
    }

    @Test
    @DisplayName("New rows default to a permanent password stamped with the current time")
    void shouldApplyPasswordLifecycleDefaults() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO users (id, email, password_hash, full_name, role)
                VALUES (?, 'astrid.lindqvist@nordicliving.se',
                        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
                        'Astrid Lindqvist', 'STAFF')
                """, id);

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT must_change_password, password_changed_at, created_at
                FROM users WHERE id = ?
                """, id);

        assertThat(row.get("must_change_password")).isEqualTo(false);
        assertThat(row.get("password_changed_at")).isNotNull();
    }
}
