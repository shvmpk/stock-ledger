package com.shvmpk.stock_ledger.platform.service;

import com.shvmpk.stock_ledger.order.entity.OrderSource;
import com.shvmpk.stock_ledger.order.service.BacklogAllocationService;
import com.shvmpk.stock_ledger.platform.adapter.ExternalOrder;
import com.shvmpk.stock_ledger.platform.adapter.PlatformOrderAdapter;
import com.shvmpk.stock_ledger.platform.adapter.PlatformOrderPage;
import com.shvmpk.stock_ledger.platform.adapter.SyncWindow;
import com.shvmpk.stock_ledger.platform.dto.PlatformSyncResponse;
import com.shvmpk.stock_ledger.platform.entity.PlatformConnection;
import com.shvmpk.stock_ledger.platform.entity.PlatformCredentials;
import com.shvmpk.stock_ledger.platform.entity.PlatformType;
import com.shvmpk.stock_ledger.platform.outbound.HardenedOutboundClient;
import com.shvmpk.stock_ledger.platform.outbound.OutboundRequest;
import com.shvmpk.stock_ledger.platform.outbound.OutboundResponse;
import com.shvmpk.stock_ledger.platform.outbound.PlatformOutboundException;
import com.shvmpk.stock_ledger.platform.repository.PlatformConnectionRepository;
import com.shvmpk.stock_ledger.platform.security.PlatformCredentialCipher;
import com.shvmpk.stock_ledger.platform.security.PlatformCredentialException;
import com.shvmpk.stock_ledger.platform.security.PlatformCredentialsCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class PlatformSyncService {

    private static final int MAX_PAGES = 20;
    private static final String API_CREDENTIALS_AAD = "apiCredentials";

    private final PlatformConnectionRepository platformConnectionRepository;
    private final PlatformSyncStatusService platformSyncStatusService;
    private final PlatformOrderIntakeService platformOrderIntakeService;
    private final BacklogAllocationService backlogAllocationService;
    private final PlatformCredentialCipher platformCredentialCipher;
    private final PlatformCredentialsCodec platformCredentialsCodec;
    private final List<PlatformOrderAdapter> platformOrderAdapters;
    private final HardenedOutboundClient outboundClient;
    private final Duration httpTimeout;

    public PlatformSyncService(
            PlatformConnectionRepository platformConnectionRepository,
            PlatformSyncStatusService platformSyncStatusService,
            PlatformOrderIntakeService platformOrderIntakeService,
            BacklogAllocationService backlogAllocationService,
            PlatformCredentialCipher platformCredentialCipher,
            PlatformCredentialsCodec platformCredentialsCodec,
            List<PlatformOrderAdapter> platformOrderAdapters,
            HardenedOutboundClient outboundClient,
            @Value("${app.platform.sync.http-timeout:10s}") Duration httpTimeout
    ) {
        this.platformConnectionRepository = platformConnectionRepository;
        this.platformSyncStatusService = platformSyncStatusService;
        this.platformOrderIntakeService = platformOrderIntakeService;
        this.backlogAllocationService = backlogAllocationService;
        this.platformCredentialCipher = platformCredentialCipher;
        this.platformCredentialsCodec = platformCredentialsCodec;
        this.platformOrderAdapters = platformOrderAdapters;
        this.outboundClient = outboundClient;
        this.httpTimeout = httpTimeout;
    }

    public PlatformSyncResponse syncConnection(UUID connectionId) {
        PlatformSyncTarget target = platformSyncStatusService.markRunning(connectionId);

        try {
            PlatformCredentials credentials = decryptCredentials(target);
            PlatformOrderAdapter adapter = adapterFor(target.platformType());
            SyncWindow window = SyncWindow.forConnection(
                    target.previousLastSyncAt(),
                    OffsetDateTime.now(ZoneOffset.UTC)
            );

            int pageCount = 0;
            String pageToken = null;
            do {
                PlatformOrderPage page = fetchPage(adapter, target, credentials, window, pageToken);
                page.orders().forEach(this::processExternalOrder);
                pageCount++;
                pageToken = page.nextPageToken();
            } while (pageToken != null && pageCount < MAX_PAGES);

            if (pageToken != null) {
                log.warn(
                        "Platform sync for connection {} stopped at the {} page cap; the next run will resume from its overlap window.",
                        connectionId,
                        MAX_PAGES
                );
            }

            return platformSyncStatusService.markSuccess(connectionId);
        } catch (PlatformOutboundException ex) {
            log.warn("Platform sync could not reach {} for connection {}: {}", target.platformType(), connectionId, ex.getMessage());
            return platformSyncStatusService.markFailed(connectionId, ex.getMessage());
        } catch (PlatformSyncException ex) {
            log.warn("Platform sync failed for connection {}: {}", connectionId, ex.getMessage());
            return platformSyncStatusService.markFailed(connectionId, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("Unexpected error during platform sync for connection {}", connectionId, ex);
            return platformSyncStatusService.markFailed(
                    connectionId,
                    "Sync failed due to an unexpected error."
            );
        }
    }

    private PlatformOrderPage fetchPage(
            PlatformOrderAdapter adapter,
            PlatformSyncTarget target,
            PlatformCredentials credentials,
            SyncWindow window,
            String pageToken
    ) {
        OutboundRequest request = adapter.buildPageRequest(
                target,
                credentials,
                window,
                pageToken,
                httpTimeout
        );
        OutboundResponse response = outboundClient.execute(request);
        validateStatus(response.statusCode());
        return adapter.parsePage(response, pageToken);
    }

    private void validateStatus(int statusCode) {
        if (statusCode >= 200 && statusCode < 300) {
            return;
        }
        if (statusCode == 401 || statusCode == 403) {
            throw new PlatformSyncException(
                    "Authentication failed (HTTP " + statusCode + "). Check platform credentials."
            );
        }
        if (statusCode == 429) {
            throw new PlatformSyncException("Rate limit exceeded (HTTP 429). Retry after backoff.");
        }
        if (statusCode == 404) {
            throw new PlatformSyncException("Platform endpoint not found (HTTP 404).");
        }
        if (statusCode >= 500) {
            throw new PlatformSyncException("Platform returned HTTP " + statusCode + ".");
        }
        throw new PlatformSyncException("Platform rejected the request (HTTP " + statusCode + ").");
    }

    private PlatformCredentials decryptCredentials(PlatformSyncTarget target) {
        if (target.encryptedApiCredentials() == null || target.encryptedApiCredentials().isBlank()) {
            throw new PlatformSyncException("Platform credentials are not configured.");
        }
        try {
            String decrypted = platformCredentialCipher.decrypt(
                    target.encryptedApiCredentials(),
                    API_CREDENTIALS_AAD
            );
            return platformCredentialsCodec.decode(decrypted);
        } catch (PlatformCredentialException ex) {
            throw new PlatformSyncException("Stored platform credentials could not be decrypted.", ex);
        }
    }

    private PlatformOrderAdapter adapterFor(PlatformType platformType) {
        return platformOrderAdapters.stream()
                .filter(adapter -> adapter.platformType() == platformType)
                .findFirst()
                .orElseThrow(() -> new PlatformSyncException(
                        "Outbound sync is not supported for " + platformType + "."
                ));
    }

    private void processExternalOrder(ExternalOrder externalOrder) {
        try {
            List<PlatformOrderIntakeService.IntakeItem> items = externalOrder.items().stream()
                    .map(item -> new PlatformOrderIntakeService.IntakeItem(
                            item.sku(),
                            item.quantity(),
                            item.unitPrice()
                    ))
                    .toList();

            platformOrderIntakeService.createWaitingOrder(
                    OrderSource.PLATFORM_SYNC,
                    externalOrder.orderNumber(),
                    externalOrder.externalOrderId(),
                    externalOrder.customerName(),
                    externalOrder.customerEmail(),
                    externalOrder.shippingAddress(),
                    externalOrder.shippingCharge(),
                    items
            ).ifPresent(intake -> allocate(intake, externalOrder.orderNumber()));
        } catch (RuntimeException ex) {
            log.error(
                    "Platform order {} could not be imported; it will be retried on the next sync.",
                    externalOrder.orderNumber(),
                    ex
            );
        }
    }

    private void allocate(PlatformOrderIntakeService.PlatformIntakeResult intake, String orderNumber) {
        intake.variantIds().stream()
                .distinct()
                .forEach(variantId -> {
                    try {
                        backlogAllocationService.allocateWaitingForVariant(variantId, null);
                    } catch (RuntimeException ex) {
                        log.error(
                                "Platform order {} was imported but backlog allocation failed for variant {}. "
                                        + "It will remain waiting for stock.",
                                orderNumber,
                                variantId,
                                ex
                        );
                    }
                });
    }

    public void syncAllActiveConnections() {
        platformConnectionRepository.findByActiveTrue().stream()
                .map(PlatformConnection::getId)
                .forEach(connectionId -> {
                    try {
                        syncConnection(connectionId);
                    } catch (RuntimeException ex) {
                        log.error("Scheduled sync failed for connection {}", connectionId, ex);
                    }
                });
    }
}
