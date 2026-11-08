package com.shvmpk.stock_ledger.platform.dto;

import com.shvmpk.stock_ledger.platform.entity.PlatformConnection;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PlatformSyncResponse(
        UUID connectionId,
        String status,
        OffsetDateTime lastSyncAt,
        String lastErrorMessage
) {
    public static PlatformSyncResponse from(PlatformConnection connection) {
        return new PlatformSyncResponse(
                connection.getId(),
                connection.getSyncStatus(),
                connection.getLastSyncAt(),
                connection.getLastErrorMessage()
        );
    }
}
