package com.shvmpk.stock_ledger.config.seed;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Applies historical timestamps to seeded rows.
 *
 * <p>The normal domain factories stamp {@code created_at} at insert time. Those columns are
 * intentionally immutable for application updates, so the dev seeder uses this dedicated
 * component to place the demo history across the last four months without weakening the
 * production entities' update rules.</p>
 */
@Component
@RequiredArgsConstructor
public class SeedDataBackdater {

    private final JdbcTemplate jdbcTemplate;

    public void backdateInitialReceipts(OffsetDateTime receivedAt) {
        Objects.requireNonNull(receivedAt, "ReceivedAt cannot be null");
        jdbcTemplate.update("""
                UPDATE stock_movements
                SET created_at = ?
                WHERE reason = 'RECEIVE'
                  AND reference_type = 'RECEIVING'
                """, Timestamp.from(receivedAt.toInstant()));
    }

    public void backdateOrder(OrderTimeline timeline) {
        Objects.requireNonNull(timeline, "Order timeline cannot be null");

        jdbcTemplate.update("""
                UPDATE orders
                SET created_at = ?, updated_at = ?
                WHERE id = ?
                """,
                Timestamp.from(timeline.createdAt().toInstant()),
                Timestamp.from(timeline.updatedAt().toInstant()),
                timeline.orderId()
        );

        if (timeline.shippedAt() != null) {
            jdbcTemplate.update(
                    "UPDATE orders SET shipped_at = ? WHERE id = ?",
                    Timestamp.from(timeline.shippedAt().toInstant()),
                    timeline.orderId()
            );
            jdbcTemplate.update("""
                    UPDATE stock_movements
                    SET created_at = ?
                    WHERE reference_id = ?
                      AND reason = 'ORDER_SHIPPED'
                    """,
                    Timestamp.from(timeline.shippedAt().toInstant()),
                    timeline.orderId()
            );
        }

        if (timeline.returnedAt() != null) {
            jdbcTemplate.update(
                    "UPDATE orders SET returned_at = ? WHERE id = ?",
                    Timestamp.from(timeline.returnedAt().toInstant()),
                    timeline.orderId()
            );
        }

        jdbcTemplate.update("""
                UPDATE stock_movements
                SET created_at = ?
                WHERE reference_id = ?
                  AND reason = 'ORDER_RESERVED'
                """,
                Timestamp.from(timeline.createdAt().plusMinutes(15).toInstant()),
                timeline.orderId()
        );

        jdbcTemplate.update("""
                UPDATE stock_movements
                SET created_at = ?
                WHERE reference_id = ?
                  AND reason = 'ORDER_CANCELLED'
                """,
                Timestamp.from(timeline.updatedAt().toInstant()),
                timeline.orderId()
        );

        if (timeline.returnedAt() != null) {
            for (UUID returnId : timeline.returnIds()) {
                jdbcTemplate.update(
                        "UPDATE order_returns SET processed_at = ? WHERE id = ?",
                        Timestamp.from(timeline.returnedAt().toInstant()),
                        returnId
                );
                jdbcTemplate.update(
                        "UPDATE stock_movements SET created_at = ? WHERE reference_id = ?",
                        Timestamp.from(timeline.returnedAt().plusHours(2).toInstant()),
                        returnId
                );
            }
        }
    }

    public record OrderTimeline(
            UUID orderId,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            OffsetDateTime shippedAt,
            OffsetDateTime returnedAt,
            List<UUID> returnIds
    ) {
        public OrderTimeline {
            Objects.requireNonNull(orderId, "OrderId cannot be null");
            Objects.requireNonNull(createdAt, "CreatedAt cannot be null");
            Objects.requireNonNull(updatedAt, "UpdatedAt cannot be null");
            returnIds = List.copyOf(returnIds);
        }
    }
}
