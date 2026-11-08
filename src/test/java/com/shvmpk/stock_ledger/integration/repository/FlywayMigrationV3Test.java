package com.shvmpk.stock_ledger.integration.repository;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@DisplayName("Flyway V3 Migration Verification Test")
class FlywayMigrationV3Test {

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    @DisplayName("V3 schema must create orders, order_items, order_returns, order_return_items, and shipping_price_list tables")
    void shouldCreateOrdersAndShippingTables() {
        List<String> tables = jdbcClient.sql("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                """)
                .query(String.class)
                .list();

        assertThat(tables)
                .as("Public schema must contain order and shipping tables")
                .contains("orders", "order_items", "order_returns", "order_return_items", "shipping_price_list");
    }

    @Test
    @DisplayName("Orders table must enforce unique order_number constraint")
    void shouldEnforceUniqueOrderNumber() {
        UUID o1 = UUID.randomUUID();
        UUID o2 = UUID.randomUUID();
        String orderNum = "ORD-2026-0001";

        jdbcClient.sql("""
                INSERT INTO orders (id, order_number, source, status, customer_name, customer_email, shipping_address, customer_shipping_charge, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                """)
                .params(o1, orderNum, "MANUAL", "PENDING", "Freja Larsson", "freja@example.se", "Storgatan 12, Stockholm", new BigDecimal("4.99"))
                .update();

        assertThatThrownBy(() -> jdbcClient.sql("""
                INSERT INTO orders (id, order_number, source, status, customer_name, customer_email, shipping_address, customer_shipping_charge, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                """)
                .params(o2, orderNum, "MANUAL", "PENDING", "Duplicate", "dup@example.se", "Another Address", new BigDecimal("0.00"))
                .update())
                .as("Duplicate order_number must violate unique constraint")
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Shipping price list must enforce unique carrier and parcel_size pair")
    void shouldEnforceUniqueCarrierAndParcelSize() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        jdbcClient.sql("""
                INSERT INTO shipping_price_list (id, carrier, parcel_size, cost, active, updated_at)
                VALUES (?, ?, ?, ?, ?, NOW())
                """)
                .params(id1, "DHL", "SMALL", new BigDecimal("5.50"), true)
                .update();

        assertThatThrownBy(() -> jdbcClient.sql("""
                INSERT INTO shipping_price_list (id, carrier, parcel_size, cost, active, updated_at)
                VALUES (?, ?, ?, ?, ?, NOW())
                """)
                .params(id2, "DHL", "SMALL", new BigDecimal("6.00"), true)
                .update())
                .as("Duplicate carrier + parcel_size must violate unique constraint")
                .isInstanceOf(Exception.class);
    }
}
