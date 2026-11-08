package com.shvmpk.stock_ledger.platform.service;

import com.shvmpk.stock_ledger.platform.repository.PlatformConnectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Keeps the inbound clock on a connection. The clock moves only when a new
 * webhook order is accepted, never on a replay, and never bumps the row
 * version.
 */
@Component
@RequiredArgsConstructor
public class PlatformInboundOrderTracker {

    private final PlatformConnectionRepository platformConnectionRepository;

    @Transactional
    public void recordInboundOrder(UUID connectionId, OffsetDateTime receivedAt) {
        if (connectionId == null) {
            return;
        }
        platformConnectionRepository.recordInboundOrder(connectionId, receivedAt);
    }
}
