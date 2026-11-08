package com.shvmpk.stock_ledger.platform.repository;

import com.shvmpk.stock_ledger.platform.entity.PlatformConnection;
import com.shvmpk.stock_ledger.platform.entity.PlatformType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlatformConnectionRepository extends JpaRepository<PlatformConnection, UUID> {

    List<PlatformConnection> findByPlatformTypeAndActiveTrue(PlatformType platformType);

    Optional<PlatformConnection> findBySlugAndActiveTrue(String slug);

    List<PlatformConnection> findByActiveTrue();

    List<PlatformConnection> findByActive(boolean active);

    List<PlatformConnection> findAllByOrderByActiveDescPlatformTypeAsc();

    boolean existsBySlugAndActiveTrueAndIdNot(String slug, UUID id);

    Optional<PlatformConnection> findByCreationRequestKey(String creationRequestKey);

    /**
     * Stores the latest test result without touching {@code version} or
     * {@code updated_at}, so an owner's open edit stays valid.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE platform_connections
            SET last_tested_at = :testedAt,
                last_test_outcome = :outcome,
                last_test_http_status = :httpStatus,
                last_test_detail = :detail
            WHERE id = :id
            """, nativeQuery = true)
    int recordTestResult(
            @Param("id") UUID id,
            @Param("testedAt") OffsetDateTime testedAt,
            @Param("outcome") String outcome,
            @Param("httpStatus") Integer httpStatus,
            @Param("detail") String detail
    );

    /**
     * Moves the inbound clock without touching {@code version} or
     * {@code updated_at}; only a genuinely new webhook order calls this.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE platform_connections
            SET last_inbound_order_at = :receivedAt
            WHERE id = :id
            """, nativeQuery = true)
    int recordInboundOrder(
            @Param("id") UUID id,
            @Param("receivedAt") OffsetDateTime receivedAt
    );
}
