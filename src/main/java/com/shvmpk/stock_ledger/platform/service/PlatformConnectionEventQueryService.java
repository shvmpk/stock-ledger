package com.shvmpk.stock_ledger.platform.service;

import com.shvmpk.stock_ledger.common.error.ResourceNotFoundException;
import com.shvmpk.stock_ledger.platform.dto.PlatformConnectionEventResponse;
import com.shvmpk.stock_ledger.platform.repository.PlatformConnectionEventRepository;
import com.shvmpk.stock_ledger.platform.repository.PlatformConnectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Read side of the platform connection history. The connection must still
 * exist to be asked about; its history itself is independent of it.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlatformConnectionEventQueryService {

    private final PlatformConnectionEventRepository eventRepository;
    private final PlatformConnectionRepository connectionRepository;

    public Page<PlatformConnectionEventResponse> history(UUID connectionId, Pageable pageable) {
        if (!connectionRepository.existsById(connectionId)) {
            throw new ResourceNotFoundException("PlatformConnection", connectionId);
        }
        return eventRepository.findByPlatformConnectionId(connectionId, pageable)
                .map(PlatformConnectionEventResponse::from);
    }
}
