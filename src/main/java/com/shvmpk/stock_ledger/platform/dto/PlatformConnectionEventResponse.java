package com.shvmpk.stock_ledger.platform.dto;

import com.shvmpk.stock_ledger.platform.entity.PlatformConnectionChangeType;
import com.shvmpk.stock_ledger.platform.entity.PlatformConnectionEvent;
import com.shvmpk.stock_ledger.platform.entity.PlatformTestOutcome;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Read model for the append-only platform connection history. It names only
 * the fields that changed; values and secrets never appear here.
 */
public record PlatformConnectionEventResponse(
        UUID id,
        UUID platformConnectionId,
        String platformType,
        PlatformConnectionChangeType changeType,
        UUID actorUserId,
        String actorEmail,
        List<String> fields,
        String reason,
        PlatformTestOutcome outcome,
        String detail,
        OffsetDateTime occurredAt
) {
    public static PlatformConnectionEventResponse from(PlatformConnectionEvent event) {
        return new PlatformConnectionEventResponse(
                event.getId(),
                event.getPlatformConnectionId(),
                event.getPlatformType(),
                event.getChangeType(),
                event.getActorUserId(),
                event.getActorEmail(),
                event.getFields(),
                event.getReason(),
                event.getOutcome(),
                event.getDetail(),
                event.getOccurredAt()
        );
    }
}
