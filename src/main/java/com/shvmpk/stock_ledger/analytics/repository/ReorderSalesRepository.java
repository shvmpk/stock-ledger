package com.shvmpk.stock_ledger.analytics.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Read-only sales aggregation for reorder guidance. The queries deliberately
 * count only genuinely shipped orders; pending, cancelled, and merely reserved
 * orders are not demand yet.
 */
@Repository
@RequiredArgsConstructor
public class ReorderSalesRepository {

    private final JdbcTemplate jdbcTemplate;

    public List<VariantSales> findVariantSales(OffsetDateTime windowStart) {
        return jdbcTemplate.query("""
                SELECT
                    oi.product_variant_id AS variant_id,
                    COALESCE(SUM(CASE WHEN o.shipped_at >= ? THEN oi.quantity ELSE 0 END), 0) AS units_shipped,
                    COUNT(DISTINCT CASE WHEN o.shipped_at >= ? THEN o.id END) AS orders_count,
                    MIN(o.shipped_at) AS first_shipped_at
                FROM order_items oi
                JOIN orders o ON o.id = oi.order_id
                WHERE o.shipped_at IS NOT NULL
                  AND o.status IN ('SHIPPED', 'PARTIALLY_RETURNED', 'RETURNED')
                GROUP BY oi.product_variant_id
                """, (resultSet, rowNumber) -> new VariantSales(
                resultSet.getObject("variant_id", UUID.class),
                resultSet.getInt("units_shipped"),
                resultSet.getInt("orders_count"),
                resultSet.getObject("first_shipped_at", OffsetDateTime.class)
        ), windowStart, windowStart);
    }

    public List<VariantReturns> findVariantReturns(OffsetDateTime windowStart) {
        return jdbcTemplate.query("""
                SELECT
                    ori.product_variant_id AS variant_id,
                    COALESCE(SUM(ori.quantity), 0) AS units_returned
                FROM order_return_items ori
                JOIN order_returns r ON r.id = ori.order_return_id
                WHERE r.processed_at >= ?
                GROUP BY ori.product_variant_id
                """, (resultSet, rowNumber) -> new VariantReturns(
                resultSet.getObject("variant_id", UUID.class),
                resultSet.getInt("units_returned")
        ), windowStart);
    }

    public List<SaleLine> findSaleLines(UUID variantId, OffsetDateTime windowStart, OffsetDateTime windowEnd) {
        return jdbcTemplate.query("""
                SELECT
                    o.id AS order_id,
                    o.order_number,
                    o.shipped_at,
                    o.status,
                    oi.quantity
                FROM order_items oi
                JOIN orders o ON o.id = oi.order_id
                WHERE oi.product_variant_id = ?
                  AND o.shipped_at IS NOT NULL
                  AND o.shipped_at >= ?
                  AND o.shipped_at < ?
                  AND o.status IN ('SHIPPED', 'PARTIALLY_RETURNED', 'RETURNED')
                ORDER BY o.shipped_at DESC, o.order_number DESC
                """, (resultSet, rowNumber) -> new SaleLine(
                resultSet.getObject("order_id", UUID.class),
                resultSet.getString("order_number"),
                resultSet.getObject("shipped_at", OffsetDateTime.class),
                resultSet.getInt("quantity"),
                resultSet.getString("status")
        ), variantId, windowStart, windowEnd);
    }

    public List<ReturnLine> findReturnLines(UUID variantId, OffsetDateTime windowStart, OffsetDateTime windowEnd) {
        return jdbcTemplate.query("""
                SELECT
                    o.id AS order_id,
                    o.order_number,
                    r.processed_at,
                    ori.quantity,
                    ori.condition
                FROM order_return_items ori
                JOIN order_returns r ON r.id = ori.order_return_id
                JOIN orders o ON o.id = r.order_id
                WHERE ori.product_variant_id = ?
                  AND r.processed_at >= ?
                  AND r.processed_at < ?
                ORDER BY r.processed_at DESC, o.order_number DESC
                """, (resultSet, rowNumber) -> new ReturnLine(
                resultSet.getObject("order_id", UUID.class),
                resultSet.getString("order_number"),
                resultSet.getObject("processed_at", OffsetDateTime.class),
                resultSet.getInt("quantity"),
                resultSet.getString("condition")
        ), variantId, windowStart, windowEnd);
    }

    public record VariantSales(
            UUID variantId,
            int unitsShipped,
            int ordersCount,
            OffsetDateTime firstShippedAt
    ) {
    }

    public record VariantReturns(UUID variantId, int unitsReturned) {
    }

    public record SaleLine(
            UUID orderId,
            String orderNumber,
            OffsetDateTime shippedAt,
            int quantity,
            String status
    ) {
    }

    public record ReturnLine(
            UUID orderId,
            String orderNumber,
            OffsetDateTime processedAt,
            int quantity,
            String condition
    ) {
    }
}
