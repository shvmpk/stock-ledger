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
@DisplayName("Flyway V2 Migration Verification Test")
class FlywayMigrationV2Test {

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    @DisplayName("V2 schema must create products, product_variants, inventory_levels, and stock_movements tables")
    void shouldCreateProductAndInventoryTables() {
        List<String> tables = jdbcClient.sql("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                """)
                .query(String.class)
                .list();

        assertThat(tables)
                .as("Public schema must contain catalog and inventory ledger tables")
                .contains("products", "product_variants", "inventory_levels", "stock_movements");
    }

    @Test
    @DisplayName("Product variants must enforce unique SKU constraint")
    void shouldEnforceUniqueSku() {
        UUID prodId = UUID.randomUUID();
        jdbcClient.sql("""
                INSERT INTO products (id, name, description, category, active, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, NOW(), NOW())
                """)
                .params(prodId, "Heavyweight Hoodie", "100% Organic Cotton", "Apparel", true)
                .update();

        UUID var1 = UUID.randomUUID();
        UUID var2 = UUID.randomUUID();
        String sku = "HOOD-NVY-XL";

        jdbcClient.sql("""
                INSERT INTO product_variants (id, product_id, sku, barcode, option_name, option_value, cost_price, selling_price, weight_grams, min_stock_threshold, active, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                """)
                .params(var1, prodId, sku, "5060123456789", "Size/Color", "Navy / XL", new BigDecimal("22.00"), new BigDecimal("55.00"), 450, 5, true)
                .update();

        assertThatThrownBy(() -> jdbcClient.sql("""
                INSERT INTO product_variants (id, product_id, sku, barcode, option_name, option_value, cost_price, selling_price, weight_grams, min_stock_threshold, active, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                """)
                .params(var2, prodId, sku, "5060123456789", "Size/Color", "Navy / XL", new BigDecimal("22.00"), new BigDecimal("55.00"), 450, 5, true)
                .update())
                .as("Duplicate SKU must violate unique constraint")
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Inventory levels must reject negative on-hand quantities via check constraint")
    void shouldRejectNegativeOnHandQuantity() {
        UUID prodId = UUID.randomUUID();
        jdbcClient.sql("""
                INSERT INTO products (id, name, category, active, created_at, updated_at)
                VALUES (?, ?, ?, ?, NOW(), NOW())
                """)
                .params(prodId, "Specialty Coffee Beans", "Coffee", true)
                .update();

        UUID varId = UUID.randomUUID();
        jdbcClient.sql("""
                INSERT INTO product_variants (id, product_id, sku, cost_price, selling_price, weight_grams, active, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                """)
                .params(varId, prodId, "COF-ETH-250G", new BigDecimal("7.50"), new BigDecimal("16.00"), 250, true)
                .update();

        UUID invId = UUID.randomUUID();
        assertThatThrownBy(() -> jdbcClient.sql("""
                INSERT INTO inventory_levels (id, product_variant_id, on_hand_quantity, reserved_quantity, version, updated_at)
                VALUES (?, ?, ?, ?, ?, NOW())
                """)
                .params(invId, varId, -5, 0, 0)
                .update())
                .as("Negative on_hand_quantity must violate check constraint")
                .isInstanceOf(Exception.class);
    }
}
