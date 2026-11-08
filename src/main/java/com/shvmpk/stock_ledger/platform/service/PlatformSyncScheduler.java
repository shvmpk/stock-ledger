package com.shvmpk.stock_ledger.platform.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.platform.sync.scheduled-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class PlatformSyncScheduler {

    private final PlatformSyncService platformSyncService;

    @Scheduled(fixedDelay = 900_000, initialDelay = 60_000)
    public void runScheduledSync() {
        log.info("Executing scheduled platform sync for all active connections...");
        platformSyncService.syncAllActiveConnections();
    }
}
