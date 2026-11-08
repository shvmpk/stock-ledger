package com.shvmpk.stock_ledger.platform.dto;

import com.shvmpk.stock_ledger.platform.entity.PlatformConnection;
import com.shvmpk.stock_ledger.platform.entity.PlatformTestOutcome;
import com.shvmpk.stock_ledger.platform.entity.PlatformType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PlatformConnectionResponse(
        UUID id,
        long version,
        PlatformType platformType,
        String displayName,
        String slug,
        String apiEndpoint,
        String webhookPath,
        String syncStatus,
        boolean syncSupported,
        OffsetDateTime lastSyncAt,
        String lastErrorMessage,
        boolean active,
        boolean webhookConfigured,
        boolean credentialsConfigured,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime deactivatedAt,
        String pauseReason,
        OffsetDateTime lastTestedAt,
        PlatformTestOutcome lastTestOutcome,
        Integer lastTestHttpStatus,
        String lastTestDetail,
        OffsetDateTime lastInboundOrderAt
) {
    public static PlatformConnectionResponse from(PlatformConnection connection) {
        PlatformType platformType = connection.getPlatformType();
        String slug = connection.getSlug() != null ? connection.getSlug() : platformType.defaultSlug();
        String webhookPath = PlatformType.WEBHOOK_ROOT + slug;
        return new PlatformConnectionResponse(
                connection.getId(),
                connection.getVersion(),
                platformType,
                connection.getDisplayName() != null ? connection.getDisplayName() : platformType.displayName(),
                slug,
                connection.getApiEndpoint(),
                webhookPath,
                connection.getSyncStatus(),
                platformType.supportsOutboundSync(),
                connection.getLastSyncAt(),
                connection.getLastErrorMessage(),
                connection.isActive(),
                connection.isWebhookConfigured(),
                connection.isCredentialsConfigured(),
                connection.getCreatedAt(),
                connection.getUpdatedAt(),
                connection.getDeactivatedAt(),
                connection.getPauseReason(),
                connection.getLastTestedAt(),
                connection.getLastTestOutcome(),
                connection.getLastTestHttpStatus(),
                connection.getLastTestDetail(),
                connection.getLastInboundOrderAt()
        );
    }
}
