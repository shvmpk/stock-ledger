package com.shvmpk.stock_ledger.platform.service;

import com.shvmpk.stock_ledger.platform.entity.PlatformType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PlatformSyncTarget(
        UUID connectionId,
        PlatformType platformType,
        String apiEndpoint,
        String encryptedApiCredentials,
        OffsetDateTime previousLastSyncAt
) {
}
