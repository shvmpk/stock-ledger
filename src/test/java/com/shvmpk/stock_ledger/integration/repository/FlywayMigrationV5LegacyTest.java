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
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DisplayName("Flyway V5 Legacy Platform Reset Test")
class FlywayMigrationV5LegacyTest {

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));

    @Test
    @DisplayName("Deactivates legacy platform rows and clears unusable secrets")
    void shouldResetLegacyPlatformRows() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target("4")
                .load()
                .migrate();

        UUID connectionId = UUID.randomUUID();
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO platform_connections
                        (id, platform_type, api_endpoint, webhook_secret_hash,
                         encrypted_api_credentials, sync_status, active)
                    VALUES ('%s', 'SHOPIFY', 'https://northwind-supply.myshopify.com',
                            'legacy-hash', 'plaintext-token', 'SUCCESS', TRUE)
                    """.formatted(connectionId));
        }

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target("5")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                     SELECT active, deactivated_at, webhook_secret_encrypted, encrypted_api_credentials
                     FROM platform_connections WHERE id = '%s'
                     """.formatted(connectionId))) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getBoolean("active")).isFalse();
            assertThat(resultSet.getTimestamp("deactivated_at")).isNotNull();
            assertThat(resultSet.getString("webhook_secret_encrypted")).isEmpty();
            assertThat(resultSet.getString("encrypted_api_credentials")).isNull();
        }
    }
}
