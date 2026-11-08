package com.shvmpk.stock_ledger.integration.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DisplayName("Flyway V16 Optional Product Variant Fields Test")
class FlywayMigrationV16Test {

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));

    @Test
    @DisplayName("Makes cost, weight and reorder level optional and converts legacy forced zeros")
    void shouldMakeOptionalVariantFieldsNullable() throws Exception {
        migrateTo("15");

        UUID productId = UUID.randomUUID();
        UUID legacyVariantId = UUID.randomUUID();
        UUID recordedVariantId = UUID.randomUUID();

        try (Connection connection = connect()) {
            insertProduct(connection, productId);
            insertVariant(
                    connection,
                    legacyVariantId,
                    productId,
                    "SWATCH-LIN-LEGACY",
                    "Natural linen",
                    new BigDecimal("0.00"),
                    new BigDecimal("14.00"),
                    0,
                    5
            );
            insertVariant(
                    connection,
                    recordedVariantId,
                    productId,
                    "SWATCH-LIN-RECORDED",
                    "Stone washed linen",
                    new BigDecimal("12.50"),
                    new BigDecimal("18.00"),
                    350,
                    4
            );
        }

        migrateTo("16");

        Map<String, ColumnDefinition> columns = readOptionalColumns();
        assertThat(columns.get("cost_price").nullable())
                .as("Cost must become optional")
                .isTrue();
        assertThat(columns.get("weight_grams").nullable())
                .as("Weight must become optional")
                .isTrue();
        assertThat(columns.get("min_stock_threshold").nullable())
                .as("Reorder level must become optional")
                .isTrue();
        assertThat(columns.get("weight_grams").defaultValue())
                .as("Unknown weight must stay blank rather than default to 0")
                .isNull();
        assertThat(columns.get("min_stock_threshold").defaultValue())
                .as("Unknown reorder level must stay blank rather than default to 5")
                .isNull();

        try (Connection connection = connect()) {
            assertThat(readOptionalValues(connection, legacyVariantId))
                    .as("Legacy forced zeros become unknown and the real reorder level survives")
                    .containsExactly(null, null, 5);
            assertThat(readOptionalValues(connection, recordedVariantId))
                    .as("Recorded values are untouched")
                    .containsExactly(new BigDecimal("12.50"), 350, 4);

            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO product_variants
                            (id, product_id, sku, selling_price, cost_price,
                             weight_grams, min_stock_threshold, active, created_at, updated_at)
                        VALUES ('%s', '%s', 'SWATCH-LIN-BLANK', 14.00, NULL, NULL, NULL, TRUE, NOW(), NOW())
                        """.formatted(UUID.randomUUID(), productId));
            }
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

    private static void insertProduct(Connection connection, UUID productId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO products (id, name, category, active, created_at, updated_at)
                VALUES (?, 'Linen Sample Swatch Set', 'Homeware', TRUE, NOW(), NOW())
                """)) {
            statement.setObject(1, productId);
            statement.executeUpdate();
        }
    }

    private static void insertVariant(
            Connection connection,
            UUID variantId,
            UUID productId,
            String sku,
            String optionValue,
            BigDecimal costPrice,
            BigDecimal sellingPrice,
            int weightGrams,
            int minStockThreshold
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO product_variants
                    (id, product_id, sku, option_name, option_value, cost_price,
                     selling_price, weight_grams, min_stock_threshold, active,
                     created_at, updated_at)
                VALUES (?, ?, ?, 'Fabric', ?, ?, ?, ?, ?, TRUE, NOW(), NOW())
                """)) {
            statement.setObject(1, variantId);
            statement.setObject(2, productId);
            statement.setString(3, sku);
            statement.setString(4, optionValue);
            statement.setBigDecimal(5, costPrice);
            statement.setBigDecimal(6, sellingPrice);
            statement.setInt(7, weightGrams);
            statement.setInt(8, minStockThreshold);
            statement.executeUpdate();
        }
    }

    private static Map<String, ColumnDefinition> readOptionalColumns() throws SQLException {
        Map<String, ColumnDefinition> columns = new HashMap<>();
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT column_name, is_nullable, column_default
                     FROM information_schema.columns
                     WHERE table_schema = 'public'
                       AND table_name = 'product_variants'
                       AND column_name IN ('cost_price', 'weight_grams', 'min_stock_threshold')
                     """);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                columns.put(
                        resultSet.getString("column_name"),
                        new ColumnDefinition(
                                "YES".equals(resultSet.getString("is_nullable")),
                                resultSet.getString("column_default")
                        )
                );
            }
        }
        return columns;
    }

    private static Object[] readOptionalValues(Connection connection, UUID variantId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT cost_price, weight_grams, min_stock_threshold
                FROM product_variants
                WHERE id = ?
                """)) {
            statement.setObject(1, variantId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return new Object[]{
                        resultSet.getBigDecimal("cost_price"),
                        resultSet.getObject("weight_grams"),
                        resultSet.getObject("min_stock_threshold")
                };
            }
        }
    }

    private record ColumnDefinition(boolean nullable, String defaultValue) {
    }
}
