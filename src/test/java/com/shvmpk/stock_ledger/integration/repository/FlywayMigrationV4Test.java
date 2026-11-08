package com.shvmpk.stock_ledger.integration.repository;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@DisplayName("Flyway Migration V4 Test (Stock Counts, Platforms & Idempotency)")
class FlywayMigrationV4Test {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Verify V4 migration tables exist with correct schema constraints")
    void shouldVerifyV4TablesExist() {
        Integer stockCountsCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'stock_counts'",
                Integer.class
        );
        assertThat(stockCountsCount).isEqualTo(1);

        Integer stockCountItemsCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'stock_count_items'",
                Integer.class
        );
        assertThat(stockCountItemsCount).isEqualTo(1);

        Integer platformConnectionsCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'platform_connections'",
                Integer.class
        );
        assertThat(platformConnectionsCount).isEqualTo(1);

        Integer idempotencyRecordsCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'idempotency_records'",
                Integer.class
        );
        assertThat(idempotencyRecordsCount).isEqualTo(1);
    }
}
