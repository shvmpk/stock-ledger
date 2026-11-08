package com.shvmpk.stock_ledger.integration.repository;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import com.shvmpk.stock_ledger.user.entity.AuthEvent;
import com.shvmpk.stock_ledger.user.entity.AuthEventType;
import com.shvmpk.stock_ledger.user.entity.AuthFailureReason;
import com.shvmpk.stock_ledger.user.repository.AuthEventRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
@DisplayName("AuthEvent Repository Tests (Append-only Audit Persistence)")
class AuthEventRepositoryTest {

    @Autowired
    private AuthEventRepository authEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("Persists JSONB details and an INET address, then reads them back")
    void shouldPersistJsonbDetailsAndInetAddress() throws Exception {
        UUID actorId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        OffsetDateTime occurredAt = OffsetDateTime.of(2026, 10, 26, 9, 15, 30, 0, ZoneOffset.UTC);

        AuthEvent event = AuthEvent.builder()
                .eventType(AuthEventType.PASSWORD_RESET)
                .actorUserId(actorId)
                .actorEmail("ingrid.bergstrom@nordicliving.se")
                .subjectUserId(subjectId)
                .subjectEmail("lars.eriksson@nordicliving.se")
                .ipAddress(InetAddress.getByName("203.0.113.42"))
                .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Chrome/141.0 Safari/537.36")
                .requestId("req_4f1c2a9b8d7e6f5a4b3c2d1e0f9a8b7c")
                .details(Map.of("reason", "Forgotten password at warehouse tablet"))
                .occurredAt(occurredAt)
                .build();

        authEventRepository.saveAndFlush(event);
        entityManager.clear();

        AuthEvent reloaded = authEventRepository.findById(event.getId()).orElseThrow();
        assertThat(reloaded.getEventType()).isEqualTo(AuthEventType.PASSWORD_RESET);
        assertThat(reloaded.getFailureReason()).isNull();
        assertThat(reloaded.getActorUserId()).isEqualTo(actorId);
        assertThat(reloaded.getActorEmail()).isEqualTo("ingrid.bergstrom@nordicliving.se");
        assertThat(reloaded.getSubjectUserId()).isEqualTo(subjectId);
        assertThat(reloaded.getSubjectEmail()).isEqualTo("lars.eriksson@nordicliving.se");
        assertThat(reloaded.getIpAddress().getHostAddress()).isEqualTo("203.0.113.42");
        assertThat(reloaded.getRequestId()).isEqualTo("req_4f1c2a9b8d7e6f5a4b3c2d1e0f9a8b7c");
        assertThat(reloaded.getDetails()).containsEntry("reason", "Forgotten password at warehouse tablet");
        assertThat(reloaded.getOccurredAt()).isEqualTo(occurredAt);

        String storedDetails = jdbcTemplate.queryForObject(
                "SELECT details::text FROM auth_events WHERE id = ?",
                String.class,
                event.getId()
        );
        assertThat(storedDetails).contains("\"reason\"").contains("Forgotten password at warehouse tablet");
    }

    @Test
    @DisplayName("Persists a failed login reason without an actor user id")
    void shouldPersistFailedLoginReason() {
        AuthEvent event = AuthEvent.builder()
                .eventType(AuthEventType.LOGIN_FAILED)
                .failureReason(AuthFailureReason.INVALID_PASSWORD)
                .actorEmail("lars.eriksson@nordicliving.se")
                .build();

        AuthEvent saved = authEventRepository.saveAndFlush(event);
        entityManager.clear();

        AuthEvent reloaded = authEventRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getFailureReason()).isEqualTo(AuthFailureReason.INVALID_PASSWORD);
        assertThat(reloaded.getActorUserId()).isNull();
        assertThat(reloaded.getDetails()).isEmpty();
    }
}
