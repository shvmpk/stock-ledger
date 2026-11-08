package com.shvmpk.stock_ledger.integration.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DisplayName("Flyway V17 Reorder Settings Test")
class FlywayMigrationV17Test {

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));

    @Test
    @DisplayName("Creates a single reorder settings row with a fourteen-day lead time")
    void shouldCreateSeededReorderSettings() throws Exception {
        migrateTo("16");
        migrateTo("17");

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT lead_time_days, updated_by, updated_at
                     FROM reorder_settings
                     """);
             ResultSet resultSet = statement.executeQuery()) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt("lead_time_days")).isEqualTo(14);
            assertThat(resultSet.getObject("updated_by")).isNull();
            assertThat(resultSet.getObject("updated_at")).isNotNull();
            assertThat(resultSet.next()).as("Exactly one settings row exists").isFalse();
        }
    }

    @Test
    @DisplayName("Rejects a lead time outside one to three hundred and sixty-five days")
    void shouldRejectLeadTimeOutsideValidRange() throws Exception {
        migrateTo("17");

        try (Connection connection = connect();
             Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate(
                    "UPDATE reorder_settings SET lead_time_days = 0"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("ck_reorder_settings_lead_time");

            assertThatThrownBy(() -> statement.executeUpdate(
                    "UPDATE reorder_settings SET lead_time_days = 366"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("ck_reorder_settings_lead_time");
        }
    }

    @Test
    @DisplayName("Rejects a second settings row and a lead-time update with an unknown actor")
    void shouldEnforceSingletonAndActorReference() throws Exception {
        migrateTo("17");

        try (Connection connection = connect();
             Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO reorder_settings (id, lead_time_days, updated_at)
                    VALUES (2, 21, NOW())
                    """))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("ck_reorder_settings_singleton");
        }

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE reorder_settings
                     SET updated_by = ?
                     WHERE id = 1
                     """)) {
            statement.setObject(1, UUID.randomUUID());
            assertThatThrownBy(statement::executeUpdate)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("fk_reorder_settings_updated_by");
        }
    }

    @Test
    @DisplayName("Accepts an owner-adjusted lead time within range")
    void shouldAcceptValidLeadTimeUpdate() throws Exception {
        migrateTo("17");

        try (Connection connection = connect();
             Statement statement = connection.createStatement()) {
            assertThat(statement.executeUpdate(
                    "UPDATE reorder_settings SET lead_time_days = 21 WHERE id = 1"))
                    .isEqualTo(1);
        }

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT lead_time_days
                     FROM reorder_settings
                     WHERE id = 1
                     """);
             ResultSet resultSet = statement.executeQuery()) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt("lead_time_days")).isEqualTo(21);
        }
    }

    private static void migrateTo(String target) {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target(target)
                .load()
                .migrate();
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );
    }
}
