package com.shvmpk.stock_ledger.user.service;

import com.shvmpk.stock_ledger.config.security.HttpRequestMetadata;
import com.shvmpk.stock_ledger.config.security.RequestMetadataResolver;
import com.shvmpk.stock_ledger.user.entity.AuthEvent;
import com.shvmpk.stock_ledger.user.entity.AuthEventType;
import com.shvmpk.stock_ledger.user.entity.AuthFailureReason;
import com.shvmpk.stock_ledger.user.entity.Role;
import com.shvmpk.stock_ledger.user.entity.User;
import com.shvmpk.stock_ledger.user.repository.AuthEventRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Writes the append-only security audit trail. The database trigger is the
 * immutable guarantee; this class is the only supported write path.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class AuthEventRecorder {

    private static final Logger SECURITY_AUDIT = LoggerFactory.getLogger("SECURITY_AUDIT");

    private static final int MAX_EMAIL_LENGTH = 255;
    private static final int MAX_USER_AGENT_LENGTH = 512;
    private static final int MAX_REQUEST_ID_LENGTH = 64;
    private static final int MAX_DETAIL_VALUE_LENGTH = 512;
    private static final int MAX_DETAILS_BYTES = 2048;
    private static final Pattern SENSITIVE_KEY =
            Pattern.compile("(?i).*(password|secret|token|hash|session).*");

    private final AuthEventRepository authEventRepository;
    private final RequestMetadataResolver requestMetadataResolver;
    private final AuthMetrics authMetrics;

    public void record(AuthEventCommand command) {
        HttpRequestMetadata metadata = requestMetadataResolver.resolveCurrent();

        AuthEvent event = AuthEvent.builder()
                .eventType(command.eventType())
                .failureReason(command.failureReason())
                .actorUserId(command.actorUserId())
                .actorEmail(truncate(command.actorEmail(), MAX_EMAIL_LENGTH))
                .subjectUserId(command.subjectUserId())
                .subjectEmail(truncate(command.subjectEmail(), MAX_EMAIL_LENGTH))
                .ipAddress(metadata.ipAddress())
                .userAgent(truncate(metadata.userAgent(), MAX_USER_AGENT_LENGTH))
                .requestId(truncate(metadata.requestId(), MAX_REQUEST_ID_LENGTH))
                .details(sanitizeDetails(command.details()))
                .build();

        try {
            authEventRepository.saveAndFlush(event);
        } catch (RuntimeException exception) {
            authMetrics.auditWriteFailed();
            SECURITY_AUDIT.error(
                    "audit.write_failure event={} requestId={}",
                    command.eventType(),
                    metadata.requestId(),
                    exception
            );
            throw exception;
        }

        SECURITY_AUDIT.info(
                "event={} actorEmail={} subjectEmail={} outcome={} requestId={}",
                event.getEventType(),
                event.getActorEmail(),
                event.getSubjectEmail(),
                command.failureReason() == null ? "success" : "failure",
                event.getRequestId()
        );
    }

    public void recordLoginSucceeded(User user) {
        record(new AuthEventCommand(
                AuthEventType.LOGIN_SUCCEEDED, null,
                user.getId(), user.getEmail(),
                user.getId(), user.getEmail(),
                Map.of()
        ));
    }

    public void recordLoginFailed(String attemptedEmail, User matchedUser, AuthFailureReason reason) {
        record(new AuthEventCommand(
                AuthEventType.LOGIN_FAILED, reason,
                null, attemptedEmail,
                matchedUser == null ? null : matchedUser.getId(),
                matchedUser == null ? null : matchedUser.getEmail(),
                Map.of()
        ));
    }

    public void recordLogout(User user) {
        record(new AuthEventCommand(
                AuthEventType.LOGOUT, null,
                user.getId(), user.getEmail(),
                user.getId(), user.getEmail(),
                Map.of()
        ));
    }

    public void recordPasswordChanged(User user) {
        record(new AuthEventCommand(
                AuthEventType.PASSWORD_CHANGED, null,
                user.getId(), user.getEmail(),
                user.getId(), user.getEmail(),
                Map.of()
        ));
    }

    public void recordPasswordReset(User actor, User subject) {
        record(new AuthEventCommand(
                AuthEventType.PASSWORD_RESET, null,
                actor.getId(), actor.getEmail(),
                subject.getId(), subject.getEmail(),
                Map.of()
        ));
    }

    public void recordUserCreated(User actor, User subject) {
        record(new AuthEventCommand(
                AuthEventType.USER_CREATED, null,
                actor.getId(), actor.getEmail(),
                subject.getId(), subject.getEmail(),
                Map.of()
        ));
    }

    public void recordUserDeactivated(User actor, User subject) {
        record(new AuthEventCommand(
                AuthEventType.USER_DEACTIVATED, null,
                actor.getId(), actor.getEmail(),
                subject.getId(), subject.getEmail(),
                Map.of()
        ));
    }

    public void recordUserReactivated(User actor, User subject) {
        record(new AuthEventCommand(
                AuthEventType.USER_REACTIVATED, null,
                actor.getId(), actor.getEmail(),
                subject.getId(), subject.getEmail(),
                Map.of()
        ));
    }

    public void recordRoleChanged(User actor, User subject, Role fromRole, Role toRole) {
        record(new AuthEventCommand(
                AuthEventType.ROLE_CHANGED, null,
                actor.getId(), actor.getEmail(),
                subject.getId(), subject.getEmail(),
                Map.of("fromRole", fromRole.name(), "toRole", toRole.name())
        ));
    }

    private Map<String, String> sanitizeDetails(Map<String, String> details) {
        if (details == null || details.isEmpty()) {
            return Map.of();
        }

        Map<String, String> sanitized = new LinkedHashMap<>();
        int usedBytes = 0;
        for (Map.Entry<String, String> entry : details.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || key.isBlank() || value == null || value.isBlank()) {
                continue;
            }
            if (SENSITIVE_KEY.matcher(key).matches()) {
                SECURITY_AUDIT.warn("Dropped sensitive audit detail key '{}'.", key);
                continue;
            }

            String truncated = truncate(value, MAX_DETAIL_VALUE_LENGTH);
            int entryBytes = (key + truncated).getBytes(StandardCharsets.UTF_8).length;
            if (usedBytes + entryBytes > MAX_DETAILS_BYTES) {
                break;
            }
            sanitized.put(key, truncated);
            usedBytes += entryBytes;
        }
        return Map.copyOf(sanitized);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
