package com.shvmpk.stock_ledger.platform.repository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * Serialises create requests that carry the same idempotency key.
 *
 * <p>Without this, two simultaneous submissions can both look for the key,
 * both find nothing, and both insert; the unique index would then reject one
 * of them with an error instead of returning the connection the first one
 * created. The lock is a PostgreSQL transaction-scoped advisory lock, so it is
 * released automatically on commit or rollback and needs no table of its own.
 * Different keys never block each other.</p>
 */
@Repository
@RequiredArgsConstructor
public class PlatformConnectionCreationLock {

    private final EntityManager entityManager;

    public void acquire(String idempotencyKey) {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(:key))")
                .setParameter("key", idempotencyKey)
                .getResultList();
    }
}
