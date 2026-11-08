package com.shvmpk.stock_ledger.inventory.repository;

import com.shvmpk.stock_ledger.inventory.entity.StockMovement;
import com.shvmpk.stock_ledger.inventory.entity.StockMovementReason;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    List<StockMovement> findAllByProductVariantIdOrderByCreatedAtDesc(UUID variantId);

    List<StockMovement> findAllByReferenceId(UUID referenceId);

    @EntityGraph(attributePaths = {"actor"})
    @Query("""
            SELECT m FROM StockMovement m
            WHERE LOWER(m.sku) = LOWER(COALESCE(:sku, m.sku))
              AND COALESCE(:reason, m.reason) = m.reason
              AND m.createdAt >= COALESCE(:from, m.createdAt)
              AND m.createdAt <= COALESCE(:to, m.createdAt)
            """)
    Page<StockMovement> search(
            @Param("sku") String sku,
            @Param("reason") StockMovementReason reason,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            Pageable pageable
    );

    @Query("""
            SELECT m.reason AS reason,
                   COUNT(m) AS movementCount,
                   COALESCE(SUM(m.deltaOnHand), 0) AS netOnHand,
                   COALESCE(SUM(m.deltaReserved), 0) AS netReserved,
                   MAX(m.createdAt) AS latestAt
            FROM StockMovement m
            WHERE m.productVariant.id = :variantId
              AND m.createdAt >= :from
            GROUP BY m.reason
            ORDER BY MAX(m.createdAt) DESC
            """)
    List<MovementReasonSummaryProjection> summariseByReason(
            @Param("variantId") UUID variantId,
            @Param("from") OffsetDateTime from
    );
}
