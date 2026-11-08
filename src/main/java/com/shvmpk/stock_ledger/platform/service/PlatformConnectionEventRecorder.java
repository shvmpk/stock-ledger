package com.shvmpk.stock_ledger.platform.service;

import com.shvmpk.stock_ledger.platform.entity.PlatformConnection;
import com.shvmpk.stock_ledger.platform.entity.PlatformConnectionChangeType;
import com.shvmpk.stock_ledger.platform.entity.PlatformConnectionEvent;
import com.shvmpk.stock_ledger.platform.entity.PlatformTestOutcome;
import com.shvmpk.stock_ledger.platform.repository.PlatformConnectionEventRepository;
import com.shvmpk.stock_ledger.user.entity.User;
import com.shvmpk.stock_ledger.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Writes the append-only platform connection history. The database trigger is
 * the immutable guarantee; this class is the only supported write path.
 *
 * <p>Only field names are recorded. Values, tokens, and signing secrets must
 * never reach this class. Writes are part of the caller's transaction, so a
 * failed audit write rolls the change back.</p>
 */
@Component
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PlatformConnectionEventRecorder {

    private final PlatformConnectionEventRepository eventRepository;
    private final UserRepository userRepository;

    public void recordCreated(PlatformConnection connection, UUID actorId) {
        record(connection, PlatformConnectionChangeType.CREATED, List.of(), actorId);
    }

    public void recordUpdated(PlatformConnection connection, Collection<String> fields, UUID actorId) {
        if (fields == null || fields.isEmpty()) {
            return;
        }
        record(connection, PlatformConnectionChangeType.UPDATED, List.copyOf(fields), actorId);
    }

    public void recordPaused(PlatformConnection connection, String reason, UUID actorId) {
        record(connection, PlatformConnectionChangeType.PAUSED, List.of("active"), reason, actorId);
    }

    public void recordResumed(PlatformConnection connection, UUID actorId) {
        record(connection, PlatformConnectionChangeType.RESUMED, List.of("active"), null, actorId);
    }

    public void recordTested(
            PlatformConnection connection,
            PlatformTestOutcome outcome,
            String detail,
            UUID actorId
    ) {
        User actor = actorId == null ? null : userRepository.findById(actorId).orElse(null);
        PlatformConnectionEvent event = PlatformConnectionEvent.record(
                connection.getId(),
                connection.getPlatformType(),
                PlatformConnectionChangeType.TESTED,
                actorId,
                actor == null ? null : actor.getEmail(),
                List.of(),
                null,
                outcome,
                detail
        );

        eventRepository.saveAndFlush(event);

        log.info(
                "Recorded platform connection test {} for {} connection {} by actor {}",
                outcome,
                connection.getPlatformType(),
                connection.getId(),
                actorId
        );
    }

    private void record(
            PlatformConnection connection,
            PlatformConnectionChangeType changeType,
            List<String> fields,
            UUID actorId
    ) {
        record(connection, changeType, fields, null, actorId);
    }

    private void record(
            PlatformConnection connection,
            PlatformConnectionChangeType changeType,
            List<String> fields,
            String reason,
            UUID actorId
    ) {
        User actor = actorId == null ? null : userRepository.findById(actorId).orElse(null);
        PlatformConnectionEvent event = PlatformConnectionEvent.record(
                connection.getId(),
                connection.getPlatformType(),
                changeType,
                actorId,
                actor == null ? null : actor.getEmail(),
                fields,
                reason
        );

        eventRepository.saveAndFlush(event);

        log.info(
                "Recorded platform connection change {} for {} connection {} by actor {}; fields={}",
                changeType,
                connection.getPlatformType(),
                connection.getId(),
                actorId,
                fields
        );
    }
}
