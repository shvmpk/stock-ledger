package com.shvmpk.stock_ledger.unit.user;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.shvmpk.stock_ledger.config.security.RequestMetadataResolver;
import com.shvmpk.stock_ledger.user.entity.AuthEvent;
import com.shvmpk.stock_ledger.user.entity.AuthEventType;
import com.shvmpk.stock_ledger.user.entity.AuthFailureReason;
import com.shvmpk.stock_ledger.user.entity.Role;
import com.shvmpk.stock_ledger.user.entity.User;
import com.shvmpk.stock_ledger.user.repository.AuthEventRepository;
import com.shvmpk.stock_ledger.user.service.AuthEventCommand;
import com.shvmpk.stock_ledger.user.service.AuthEventRecorder;
import com.shvmpk.stock_ledger.user.service.AuthMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AuthEventRecorder Tests")
class AuthEventRecorderTest {

    private static final String BCRYPT_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private AuthEventRepository repository;
    private SimpleMeterRegistry meterRegistry;
    private AuthEventRecorder recorder;
    private Logger auditLogger;
    private ListAppender<ILoggingEvent> auditAppender;

    @BeforeEach
    void setUp() {
        repository = mock(AuthEventRepository.class);
        meterRegistry = new SimpleMeterRegistry();
        recorder = new AuthEventRecorder(
                repository,
                new RequestMetadataResolver(false),
                new AuthMetrics(meterRegistry)
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.24");
        request.addHeader("User-Agent",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/141.0 Safari/537.36");
        request.addHeader("X-Request-Id", "req_0123456789abcdef");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        auditLogger = (Logger) LoggerFactory.getLogger("SECURITY_AUDIT");
        auditAppender = new ListAppender<>();
        auditAppender.start();
        auditLogger.addAppender(auditAppender);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        MDC.clear();
        if (auditLogger != null && auditAppender != null) {
            auditLogger.detachAppender(auditAppender);
            auditAppender.stop();
        }
    }

    @Test
    @DisplayName("Records a password reset with actor, subject, and request metadata")
    void shouldRecordPasswordResetWithMetadata() {
        User owner = user("ingrid.bergstrom@nordicliving.se", "Ingrid Bergström", Role.OWNER);
        User staff = user("lars.eriksson@nordicliving.se", "Lars Eriksson", Role.STAFF);

        recorder.recordPasswordReset(owner, staff);

        AuthEvent saved = captureSavedEvent();
        assertThat(saved.getEventType()).isEqualTo(AuthEventType.PASSWORD_RESET);
        assertThat(saved.getActorUserId()).isEqualTo(owner.getId());
        assertThat(saved.getActorEmail()).isEqualTo("ingrid.bergstrom@nordicliving.se");
        assertThat(saved.getSubjectUserId()).isEqualTo(staff.getId());
        assertThat(saved.getSubjectEmail()).isEqualTo("lars.eriksson@nordicliving.se");
        assertThat(saved.getIpAddress().getHostAddress()).isEqualTo("198.51.100.24");
        assertThat(saved.getUserAgent()).contains("Chrome/141.0");
        assertThat(saved.getRequestId()).isEqualTo("req_0123456789abcdef");
        assertThat(saved.getDetails()).isEmpty();
    }

    @Test
    @DisplayName("Records a failed login with a reason and no actor user id")
    void shouldRecordFailedLoginWithoutActor() {
        User matched = user("lars.eriksson@nordicliving.se", "Lars Eriksson", Role.STAFF);

        recorder.recordLoginFailed("lars.eriksson@nordicliving.se", matched, AuthFailureReason.INVALID_PASSWORD);

        AuthEvent saved = captureSavedEvent();
        assertThat(saved.getEventType()).isEqualTo(AuthEventType.LOGIN_FAILED);
        assertThat(saved.getFailureReason()).isEqualTo(AuthFailureReason.INVALID_PASSWORD);
        assertThat(saved.getActorUserId()).isNull();
        assertThat(saved.getActorEmail()).isEqualTo("lars.eriksson@nordicliving.se");
        assertThat(saved.getSubjectUserId()).isEqualTo(matched.getId());
    }

    @Test
    @DisplayName("Records unknown-email failures without a subject")
    void shouldRecordUnknownEmailFailure() {
        recorder.recordLoginFailed("unknown.person@nordicliving.se", null, AuthFailureReason.UNKNOWN_EMAIL);

        AuthEvent saved = captureSavedEvent();
        assertThat(saved.getFailureReason()).isEqualTo(AuthFailureReason.UNKNOWN_EMAIL);
        assertThat(saved.getSubjectUserId()).isNull();
        assertThat(saved.getSubjectEmail()).isNull();
        assertThat(saved.getActorEmail()).isEqualTo("unknown.person@nordicliving.se");
    }

    @Test
    @DisplayName("Records the role transition in the details map")
    void shouldRecordRoleChangeDetails() {
        User owner = user("ingrid.bergstrom@nordicliving.se", "Ingrid Bergström", Role.OWNER);
        User staff = user("lars.eriksson@nordicliving.se", "Lars Eriksson", Role.STAFF);

        recorder.recordRoleChanged(owner, staff, Role.STAFF, Role.OWNER);

        AuthEvent saved = captureSavedEvent();
        assertThat(saved.getEventType()).isEqualTo(AuthEventType.ROLE_CHANGED);
        assertThat(saved.getDetails())
                .containsEntry("fromRole", "STAFF")
                .containsEntry("toRole", "OWNER");
    }

    @Test
    @DisplayName("Drops detail keys that could carry credentials")
    void shouldDropSensitiveDetailKeys() {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("password", "FjordLantern-2026!");
        details.put("apiToken", "shpat_9f4c2a7b1e8d6350");
        details.put("sessionId", "8B1F3C5D7E9A");
        details.put("reason", "Owner requested a reset");

        recorder.record(new AuthEventCommand(
                AuthEventType.PASSWORD_RESET,
                null,
                UUID.randomUUID(),
                "ingrid.bergstrom@nordicliving.se",
                UUID.randomUUID(),
                "lars.eriksson@nordicliving.se",
                details
        ));

        AuthEvent saved = captureSavedEvent();
        assertThat(saved.getDetails())
                .containsOnlyKeys("reason")
                .containsEntry("reason", "Owner requested a reset");
    }

    @Test
    @DisplayName("Truncates oversized user agent and email values, and rejects an invalid request id")
    void shouldTruncateOversizedMetadata() {
        String longUserAgent = "Mozilla/5.0 " + "a".repeat(600);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.24");
        request.addHeader("User-Agent", longUserAgent);
        request.addHeader("X-Request-Id", "req_" + "b".repeat(120));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        String longEmail = "c".repeat(300) + "@nordicliving.se";
        User owner = user(longEmail, "Ingrid Bergström", Role.OWNER);
        User staff = user("lars.eriksson@nordicliving.se", "Lars Eriksson", Role.STAFF);

        recorder.recordPasswordReset(owner, staff);

        AuthEvent saved = captureSavedEvent();
        assertThat(saved.getUserAgent()).hasSize(512);
        assertThat(saved.getRequestId())
                .as("A request id outside the accepted pattern is rejected, not truncated")
                .isNull();
        assertThat(saved.getActorEmail()).hasSize(255);
    }

    @Test
    @DisplayName("Caps the total serialized details size")
    void shouldCapDetailsSize() {
        Map<String, String> details = new LinkedHashMap<>();
        for (int index = 0; index < 20; index++) {
            details.put("entry" + index, "x".repeat(500));
        }

        recorder.record(new AuthEventCommand(
                AuthEventType.PASSWORD_RESET,
                null,
                null,
                "ingrid.bergstrom@nordicliving.se",
                null,
                null,
                details
        ));

        AuthEvent saved = captureSavedEvent();
        int totalBytes = saved.getDetails().entrySet().stream()
                .mapToInt(entry -> (entry.getKey() + entry.getValue()).getBytes(StandardCharsets.UTF_8).length)
                .sum();
        assertThat(totalBytes).isLessThanOrEqualTo(2048);
        assertThat(saved.getDetails()).isNotEmpty();
    }

    @Test
    @DisplayName("Writes exactly one security audit line per recorded event")
    void shouldWriteOneAuditLogLinePerEvent() {
        recorder.recordLoginFailed(
                "lars.eriksson@nordicliving.se",
                null,
                AuthFailureReason.UNKNOWN_EMAIL
        );

        assertThat(auditAppender.list).hasSize(1);
        ILoggingEvent logged = auditAppender.list.getFirst();
        assertThat(logged.getLevel()).isEqualTo(Level.INFO);
        assertThat(logged.getFormattedMessage())
                .contains("event=LOGIN_FAILED")
                .contains("actorEmail=lars.eriksson@nordicliving.se")
                .contains("outcome=failure")
                .contains("requestId=req_0123456789abcdef");
        assertThat(logged.getFormattedMessage()).doesNotContain(BCRYPT_HASH);
    }

    @Test
    @DisplayName("A repository failure increments the counter, logs at ERROR, and rethrows")
    void shouldLogAuditWriteFailure() {
        when(repository.saveAndFlush(any(AuthEvent.class)))
                .thenThrow(new DataIntegrityViolationException("audit_events insert rejected"));

        User owner = user("ingrid.bergstrom@nordicliving.se", "Ingrid Bergström", Role.OWNER);

        assertThatThrownBy(() -> recorder.recordPasswordChanged(owner))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(meterRegistry.get("stockledger.auth.audit.write_failure").counter().count())
                .isEqualTo(1);

        ILoggingEvent failure = auditAppender.list.stream()
                .filter(event -> event.getLevel() == Level.ERROR)
                .findFirst()
                .orElseThrow();
        assertThat(failure.getFormattedMessage())
                .contains("audit.write_failure")
                .contains("event=PASSWORD_CHANGED")
                .contains("requestId=req_0123456789abcdef");
        assertThat(failure.getFormattedMessage())
                .doesNotContain(BCRYPT_HASH)
                .doesNotContain("ingrid.bergstrom@nordicliving.se");
    }

    private AuthEvent captureSavedEvent() {
        ArgumentCaptor<AuthEvent> captor = ArgumentCaptor.forClass(AuthEvent.class);
        verify(repository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private User user(String email, String fullName, Role role) {
        return User.create(UUID.randomUUID(), email, BCRYPT_HASH, fullName, role);
    }
}
