package com.shvmpk.stock_ledger.integration.repository;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import com.shvmpk.stock_ledger.platform.entity.PlatformConnectionChangeType;
import com.shvmpk.stock_ledger.platform.entity.PlatformConnectionEvent;
import com.shvmpk.stock_ledger.platform.entity.PlatformType;
import com.shvmpk.stock_ledger.platform.repository.PlatformConnectionEventRepository;
import com.shvmpk.stock_ledger.support.PlatformConnectionEventTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
@DisplayName("Platform Connection Event Repository Tests")
class PlatformConnectionEventRepositoryTest {

    private static final UUID NORTHWIND_CONNECTION_ID =
            UUID.fromString("6c1d2e3f-4a5b-4c6d-8e9f-0a1b2c3d4e5f");
    private static final UUID HARBOUR_CONNECTION_ID =
            UUID.fromString("9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d");
    private static final UUID ACTOR_ID =
            UUID.fromString("2f4a6c8e-0b1d-4f3a-9c5e-7a9b1d3f5c7e");
    private static final String ACTOR_EMAIL = "elin.sandberg@northwind-supply.co";

    @Autowired
    private PlatformConnectionEventRepository eventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        PlatformConnectionEventTestSupport.clear(jdbcTemplate);
    }

    @Test
    @DisplayName("Persists affected field names as JSONB and reads them back")
    void shouldPersistAffectedFieldsAsJsonb() {
        PlatformConnectionEvent event = PlatformConnectionEvent.record(
                NORTHWIND_CONNECTION_ID,
                PlatformType.SHOPIFY,
                PlatformConnectionChangeType.UPDATED,
                ACTOR_ID,
                ACTOR_EMAIL,
                List.of("apiEndpoint", "webhookSecret")
        );

        eventRepository.saveAndFlush(event);
        entityManager.clear();

        PlatformConnectionEvent reloaded = eventRepository.findById(event.getId()).orElseThrow();
        assertThat(reloaded.getPlatformConnectionId()).isEqualTo(NORTHWIND_CONNECTION_ID);
        assertThat(reloaded.getPlatformType()).isEqualTo("SHOPIFY");
        assertThat(reloaded.getChangeType()).isEqualTo(PlatformConnectionChangeType.UPDATED);
        assertThat(reloaded.getActorUserId()).isEqualTo(ACTOR_ID);
        assertThat(reloaded.getActorEmail()).isEqualTo(ACTOR_EMAIL);
        assertThat(reloaded.getFields()).containsExactly("apiEndpoint", "webhookSecret");
        assertThat(reloaded.getOccurredAt()).isNotNull();

        String storedFields = jdbcTemplate.queryForObject(
                "SELECT fields::text FROM platform_connection_events WHERE id = ?",
                String.class,
                event.getId()
        );
        assertThat(storedFields).contains("apiEndpoint").contains("webhookSecret");
    }

    @Test
    @DisplayName("Persists the reason for a pause and leaves it empty for other changes")
    void shouldPersistPauseReason() {
        PlatformConnectionEvent paused = PlatformConnectionEvent.record(
                NORTHWIND_CONNECTION_ID,
                PlatformType.SHOPIFY,
                PlatformConnectionChangeType.PAUSED,
                ACTOR_ID,
                ACTOR_EMAIL,
                List.of("active"),
                "Store closed for a seasonal break."
        );
        PlatformConnectionEvent created = PlatformConnectionEvent.record(
                NORTHWIND_CONNECTION_ID,
                PlatformType.SHOPIFY,
                PlatformConnectionChangeType.CREATED,
                ACTOR_ID,
                ACTOR_EMAIL,
                List.of()
        );

        eventRepository.saveAllAndFlush(List.of(paused, created));
        entityManager.clear();

        PlatformConnectionEvent reloadedPaused = eventRepository.findById(paused.getId()).orElseThrow();
        PlatformConnectionEvent reloadedCreated = eventRepository.findById(created.getId()).orElseThrow();

        assertThat(reloadedPaused.getReason()).isEqualTo("Store closed for a seasonal break.");
        assertThat(reloadedCreated.getReason()).isNull();
    }

    @Test
    @DisplayName("Returns one connection's history newest first")
    void shouldReturnHistoryForOneConnectionNewestFirst() {
        insertEvent(NORTHWIND_CONNECTION_ID, "CREATED", "2026-10-28T09:00:00Z");
        insertEvent(NORTHWIND_CONNECTION_ID, "PAUSED", "2026-10-28T10:00:00Z");
        insertEvent(HARBOUR_CONNECTION_ID, "CREATED", "2026-10-28T09:30:00Z");

        Page<PlatformConnectionEvent> page = eventRepository.findByPlatformConnectionId(
                NORTHWIND_CONNECTION_ID,
                PageRequest.of(0, 10, Sort.by(
                        Sort.Order.desc("occurredAt"),
                        Sort.Order.desc("id")
                ))
        );

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent())
                .extracting(PlatformConnectionEvent::getChangeType)
                .containsExactly(
                        PlatformConnectionChangeType.PAUSED,
                        PlatformConnectionChangeType.CREATED
                );
    }

    private void insertEvent(UUID connectionId, String changeType, String occurredAt) {
        jdbcTemplate.update("""
                INSERT INTO platform_connection_events
                    (id, platform_connection_id, platform_type, change_type,
                     actor_user_id, actor_email, fields, occurred_at)
                VALUES (?, ?, 'SHOPIFY', ?, ?, ?, '["active"]'::jsonb, ?::timestamptz)
                """, UUID.randomUUID(), connectionId, changeType, ACTOR_ID, ACTOR_EMAIL,
                OffsetDateTime.parse(occurredAt));
    }
}
