package com.shvmpk.stock_ledger.integration.repository;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@DisplayName("Flyway V1 Migration Verification Test")
class FlywayMigrationV1Test {

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    @DisplayName("V1 schema must create users and refresh_tokens tables with expected columns")
    void shouldCreateUsersAndRefreshTokensTables() {
        List<String> tables = jdbcClient.sql("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                """)
                .query(String.class)
                .list();

        assertThat(tables)
                .as("Public schema must contain users and refresh_tokens tables")
                .contains("users", "refresh_tokens");
    }

    @Test
    @DisplayName("Users table must enforce unique email constraint")
    void shouldEnforceUniqueEmailConstraint() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        String email = "clara.merchant@nordicapparel.com";

        jdbcClient.sql("""
                INSERT INTO users (id, email, password_hash, full_name, role, active, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())
                """)
                .params(id1, email, "hashed_pw_1", "Clara Merchant", "OWNER", true)
                .update();

        assertThatThrownBy(() -> jdbcClient.sql("""
                INSERT INTO users (id, email, password_hash, full_name, role, active, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())
                """)
                .params(id2, email, "hashed_pw_2", "Duplicate User", "STAFF", true)
                .update())
                .as("Duplicate email must violate unique constraint")
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Users table must restrict role to OWNER and STAFF via check constraint")
    void shouldRejectInvalidRoleConstraint() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> jdbcClient.sql("""
                INSERT INTO users (id, email, password_hash, full_name, role, active, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())
                """)
                .params(id, "hacker@test.com", "hashed_pw", "Hacker", "SUPERADMIN", true)
                .update())
                .as("Invalid role must violate check constraint")
                .isInstanceOf(Exception.class);
    }
}
