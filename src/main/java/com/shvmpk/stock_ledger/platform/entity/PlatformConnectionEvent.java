package com.shvmpk.stock_ledger.platform.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Immutable history of a platform connection change. The platform type and
 * connection id are snapshotted rather than joined, so the record survives any
 * future retirement of the connection. The database also enforces append-only
 * semantics with a trigger.
 */
@Entity
@Table(name = "platform_connection_events")
@Immutable
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlatformConnectionEvent {

    @Id
    private UUID id;

    @Column(name = "platform_connection_id", nullable = false, updatable = false)
    private UUID platformConnectionId;

    @Column(name = "platform_type", nullable = false, length = 50, updatable = false)
    private String platformType;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 40, updatable = false)
    private PlatformConnectionChangeType changeType;

    @Column(name = "actor_user_id", updatable = false)
    private UUID actorUserId;

    @Column(name = "actor_email", length = 255, updatable = false)
    private String actorEmail;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fields", nullable = false, columnDefinition = "jsonb", updatable = false)
    private List<String> fields;

    /**
     * The plain-language reason supplied for a pause. It is a value, not a
     * secret, and is never used for any other change kind.
     */
    @Column(name = "reason", length = 500, updatable = false)
    private String reason;

    /**
     * The result of a connection test. Only {@code TESTED} rows carry these;
     * the detail is a plain-language message and never a credential.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", length = 40, updatable = false)
    private PlatformTestOutcome outcome;

    @Column(name = "detail", length = 500, updatable = false)
    private String detail;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private OffsetDateTime occurredAt;

    public static PlatformConnectionEvent record(
            UUID platformConnectionId,
            PlatformType platformType,
            PlatformConnectionChangeType changeType,
            UUID actorUserId,
            String actorEmail,
            List<String> fields
    ) {
        return record(
                platformConnectionId,
                platformType,
                changeType,
                actorUserId,
                actorEmail,
                fields,
                null
        );
    }

    public static PlatformConnectionEvent record(
            UUID platformConnectionId,
            PlatformType platformType,
            PlatformConnectionChangeType changeType,
            UUID actorUserId,
            String actorEmail,
            List<String> fields,
            String reason
    ) {
        return record(
                platformConnectionId,
                platformType,
                changeType,
                actorUserId,
                actorEmail,
                fields,
                reason,
                null,
                null
        );
    }

    public static PlatformConnectionEvent record(
            UUID platformConnectionId,
            PlatformType platformType,
            PlatformConnectionChangeType changeType,
            UUID actorUserId,
            String actorEmail,
            List<String> fields,
            String reason,
            PlatformTestOutcome outcome,
            String detail
    ) {
        return new PlatformConnectionEvent(
                UUID.randomUUID(),
                platformConnectionId,
                platformType.name(),
                changeType,
                actorUserId,
                actorEmail,
                fields == null ? List.of() : List.copyOf(fields),
                reason,
                outcome,
                detail,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }
}
