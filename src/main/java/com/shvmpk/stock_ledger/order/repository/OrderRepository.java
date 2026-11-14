package com.shvmpk.stock_ledger.order.repository;

import com.shvmpk.stock_ledger.order.entity.Order;
import com.shvmpk.stock_ledger.order.entity.OrderSource;
import com.shvmpk.stock_ledger.order.entity.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "items")
    Optional<Order> findByOrderNumber(String orderNumber);

    boolean existsByOrderNumber(String orderNumber);

    boolean existsBySourceAndExternalOrderId(OrderSource source, String externalOrderId);

    // Atomic order-number suffix source. A sequence, not a random draw:
    // concurrent orders each receive the next value, so numbers can never
    // collide however many checkouts race (see V18 migration).
    @Query(value = "SELECT nextval('order_number_seq')", nativeQuery = true)
    long nextOrderNumberSuffix();

    Page<Order> findAllByStatus(OrderStatus status, Pageable pageable);

    @Query("""
            SELECT o.status AS status, COUNT(o) AS count
            FROM Order o
            GROUP BY o.status
            """)
    List<OrderStatusCountProjection> countByStatusGrouped();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            SELECT o.id
            FROM Order o
            WHERE o.status = :status
              AND EXISTS (
                  SELECT i.id
                  FROM OrderItem i
                  WHERE i.order = o
                    AND i.productVariant.id = :variantId
              )
            ORDER BY o.createdAt ASC, o.orderNumber ASC
            """)
    List<UUID> findWaitingOrderIdsByVariant(
            @Param("variantId") UUID variantId,
            @Param("status") OrderStatus status,
            Pageable pageable
    );

    default List<UUID> findWaitingOrderIdsByVariant(UUID variantId, Pageable pageable) {
        return findWaitingOrderIdsByVariant(variantId, OrderStatus.WAITING_FOR_STOCK, pageable);
    }

    @Query("""
            SELECT o.id
            FROM Order o
            WHERE o.status = :status
            ORDER BY o.createdAt ASC, o.orderNumber ASC
            """)
    List<UUID> findAllWaitingOrderIds(@Param("status") OrderStatus status, Pageable pageable);

    default List<UUID> findAllWaitingOrderIds(Pageable pageable) {
        return findAllWaitingOrderIds(OrderStatus.WAITING_FOR_STOCK, pageable);
    }

    /**
     * Reservation holders are the open order lines that still hold stock for
     * a variant. Shipped, cancelled, and returned orders no longer hold any.
     */
    @Query("""
            SELECT o.id AS orderId,
                   o.orderNumber AS orderNumber,
                   o.status AS status,
                   i.reservedQuantity AS reservedQuantity,
                   o.createdAt AS orderDate
            FROM OrderItem i
            JOIN i.order o
            WHERE i.productVariant.id = :variantId
              AND i.reservedQuantity > 0
              AND o.status IN :statuses
            ORDER BY o.createdAt ASC, o.orderNumber ASC
            """)
    List<ReservationHolderProjection> findOpenReservationHolders(
            @Param("variantId") UUID variantId,
            @Param("statuses") List<OrderStatus> statuses
    );

    default List<ReservationHolderProjection> findOpenReservationHolders(UUID variantId) {
        return findOpenReservationHolders(variantId, List.of(OrderStatus.PENDING, OrderStatus.READY_TO_SHIP));
    }

    @org.springframework.data.jpa.repository.Query(value = """
        SELECT 
            TO_CHAR(o.shipped_at, 'YYYY-MM') AS period,
            COUNT(o.id) AS totalShipped,
            COALESCE(SUM(o.customer_shipping_charge), 0) AS totalCharged,
            COALESCE(SUM(o.actual_shipping_cost), 0) AS totalCost
        FROM orders o
        WHERE o.shipped_at IS NOT NULL
          AND (:period IS NULL OR TO_CHAR(o.shipped_at, 'YYYY-MM') = :period)
        GROUP BY TO_CHAR(o.shipped_at, 'YYYY-MM')
        ORDER BY period DESC
    """, nativeQuery = true)
    java.util.List<ShippingSummaryProjection> findMonthlyShippingSummaries(@org.springframework.data.repository.query.Param("period") String period);
}
