package com.shvmpk.stock_ledger.inventory.repository;

import com.shvmpk.stock_ledger.inventory.entity.StockCount;
import com.shvmpk.stock_ledger.inventory.entity.StockCountStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockCountRepository extends JpaRepository<StockCount, UUID> {

    @EntityGraph(attributePaths = "conductedBy")
    Page<StockCount> findAllByStatus(StockCountStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "conductedBy")
    @Query("SELECT s FROM StockCount s")
    Page<StockCount> findAllSessions(Pageable pageable);

    @Query("""
            SELECT DISTINCT s FROM StockCount s
            LEFT JOIN FETCH s.conductedBy
            LEFT JOIN FETCH s.items i
            LEFT JOIN FETCH i.productVariant pv
            LEFT JOIN FETCH pv.product
            WHERE s.id = :id
            """)
    Optional<StockCount> findWithItemsById(@Param("id") UUID id);
}
