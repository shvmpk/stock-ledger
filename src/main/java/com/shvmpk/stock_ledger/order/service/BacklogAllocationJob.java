package com.shvmpk.stock_ledger.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BacklogAllocationJob {

    private final BacklogAllocationService backlogAllocationService;

    @Scheduled(fixedDelay = 300_000, initialDelay = 120_000)
    public void allocateAllWaiting() {
        try {
            List<String> allocated = backlogAllocationService.allocateAllWaiting(null);
            if (!allocated.isEmpty()) {
                log.info("Backlog allocation sweep allocated {} waiting orders.", allocated.size());
            }
        } catch (RuntimeException ex) {
            log.error("Backlog allocation sweep failed; the next sweep will retry.", ex);
        }
    }
}
