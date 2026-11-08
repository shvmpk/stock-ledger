package com.shvmpk.stock_ledger.platform.service;

import com.shvmpk.stock_ledger.common.error.ResourceNotFoundException;
import com.shvmpk.stock_ledger.platform.dto.PlatformSyncResponse;
import com.shvmpk.stock_ledger.platform.entity.PlatformConnection;
import com.shvmpk.stock_ledger.platform.repository.PlatformConnectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlatformSyncStatusService {

    private final PlatformConnectionRepository platformConnectionRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PlatformSyncTarget markRunning(UUID connectionId) {
        PlatformConnection connection = findConnection(connectionId);
        OffsetDateTime previousLastSyncAt = connection.getLastSyncAt();
        connection.updateSyncStatus("RUNNING", null);
        platformConnectionRepository.saveAndFlush(connection);

        return new PlatformSyncTarget(
                connection.getId(),
                connection.getPlatformType(),
                connection.getApiEndpoint(),
                connection.getEncryptedApiCredentials(),
                previousLastSyncAt
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PlatformSyncResponse markSuccess(UUID connectionId) {
        PlatformConnection connection = findConnection(connectionId);
        connection.updateSyncStatus("SUCCESS", null);
        platformConnectionRepository.saveAndFlush(connection);
        return PlatformSyncResponse.from(connection);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PlatformSyncResponse markFailed(UUID connectionId, String errorMessage) {
        PlatformConnection connection = findConnection(connectionId);
        connection.updateSyncStatus("FAILED", sanitizeErrorMessage(errorMessage));
        platformConnectionRepository.saveAndFlush(connection);
        return PlatformSyncResponse.from(connection);
    }

    private PlatformConnection findConnection(UUID connectionId) {
        return platformConnectionRepository.findById(connectionId)
                .orElseThrow(() -> new ResourceNotFoundException("PlatformConnection", connectionId));
    }

    private String sanitizeErrorMessage(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return "Sync failed.";
        }
        String trimmed = errorMessage.trim();
        return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
    }
}
