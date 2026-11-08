package com.shvmpk.stock_ledger.platform.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "idempotency_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyRecord {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "request_hash")
    private String requestHash;

    @Column(name = "response_status", nullable = false)
    private int responseStatus;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    public static IdempotencyRecord create(
            String idempotencyKey,
            String requestHash,
            int responseStatus,
            String responseBody,
            int expireDays
    ) {
        IdempotencyRecord rec = new IdempotencyRecord();
        rec.id = UUID.randomUUID();
        rec.idempotencyKey = Objects.requireNonNull(idempotencyKey, "IdempotencyKey cannot be null");
        rec.requestHash = requestHash;
        rec.responseStatus = responseStatus;
        rec.responseBody = responseBody;
        rec.createdAt = OffsetDateTime.now();
        rec.expiresAt = OffsetDateTime.now().plusDays(expireDays > 0 ? expireDays : 7);
        return rec;
    }
}
