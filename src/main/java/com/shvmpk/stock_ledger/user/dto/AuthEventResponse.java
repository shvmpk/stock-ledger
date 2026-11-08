package com.shvmpk.stock_ledger.user.dto;

import com.shvmpk.stock_ledger.user.entity.AuthEvent;
import com.shvmpk.stock_ledger.user.entity.AuthEventType;
import com.shvmpk.stock_ledger.user.entity.AuthFailureReason;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Read model for the owner-only security log. The IP address is serialized as
 * a string on purpose; {@code InetAddress} does not have a stable JSON shape.
 */
public record AuthEventResponse(
        UUID id,
        AuthEventType eventType,
        AuthFailureReason failureReason,
        UUID actorUserId,
        String actorEmail,
        UUID subjectUserId,
        String subjectEmail,
        String ipAddress,
        String userAgent,
        String requestId,
        Map<String, String> details,
        OffsetDateTime occurredAt
) {
    public static AuthEventResponse from(AuthEvent event) {
        return new AuthEventResponse(
                event.getId(),
                event.getEventType(),
                event.getFailureReason(),
                event.getActorUserId(),
                event.getActorEmail(),
                event.getSubjectUserId(),
                event.getSubjectEmail(),
                event.getIpAddress() == null ? null : event.getIpAddress().getHostAddress(),
                event.getUserAgent(),
                event.getRequestId(),
                event.getDetails(),
                event.getOccurredAt()
        );
    }
}
