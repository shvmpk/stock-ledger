package com.shvmpk.stock_ledger.platform.service;

import com.shvmpk.stock_ledger.common.error.ResourceNotFoundException;
import com.shvmpk.stock_ledger.platform.adapter.PlatformOrderAdapter;
import com.shvmpk.stock_ledger.platform.dto.PlatformConnectionTestResponse;
import com.shvmpk.stock_ledger.platform.entity.PlatformConnection;
import com.shvmpk.stock_ledger.platform.entity.PlatformCredentials;
import com.shvmpk.stock_ledger.platform.entity.PlatformTestOutcome;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Tests one connection without changing it. A failed test never blocks a save;
 * the owner can fix the credential and try again.
 *
 * <p>The result is stored with a native update so the row version does not
 * move, and an inbound-only channel is never called out to.</p>
 */
@Slf4j
@Service
public class PlatformConnectionTestService {

    private static final String API_CREDENTIALS_AAD = "apiCredentials";
    private static final int MAX_DETAIL_LENGTH = 500;
    private static final DateTimeFormatter INBOUND_FORMAT =
            DateTimeFormatter.ofPattern("d MMM uuuu, HH:mm", Locale.ENGLISH)
                    .withZone(ZoneOffset.UTC);

    private final PlatformConnectionRepository platformConnectionRepository;
    private final PlatformCredentialCipher platformCredentialCipher;
    private final PlatformCredentialsCodec platformCredentialsCodec;
    private final List<PlatformOrderAdapter> platformOrderAdapters;
    private final HardenedOutboundClient outboundClient;
    private final PlatformConnectionEventRecorder eventRecorder;
    private final Duration httpTimeout;

    public PlatformConnectionTestService(
            PlatformConnectionRepository platformConnectionRepository,
            PlatformCredentialCipher platformCredentialCipher,
            PlatformCredentialsCodec platformCredentialsCodec,
            List<PlatformOrderAdapter> platformOrderAdapters,
            HardenedOutboundClient outboundClient,
            PlatformConnectionEventRecorder eventRecorder,
            @Value("${app.platform.sync.http-timeout:10s}") Duration httpTimeout
    ) {
        this.platformConnectionRepository = platformConnectionRepository;
        this.platformCredentialCipher = platformCredentialCipher;
        this.platformCredentialsCodec = platformCredentialsCodec;
        this.platformOrderAdapters = platformOrderAdapters;
        this.outboundClient = outboundClient;
        this.eventRecorder = eventRecorder;
        this.httpTimeout = httpTimeout;
    }

    @Transactional
    public PlatformConnectionTestResponse test(UUID connectionId, UUID actorId) {
        PlatformConnection connection = platformConnectionRepository.findById(connectionId)
                .orElseThrow(() -> new ResourceNotFoundException("PlatformConnection", connectionId));

        if (!connection.getPlatformType().supportsOutboundSync()) {
            return awaitingInbound(connection);
        }

        OffsetDateTime testedAt = OffsetDateTime.now(ZoneOffset.UTC);
        TestResult result = runTest(connection);

        String detail = truncate(result.message());
        platformConnectionRepository.recordTestResult(
                connection.getId(),
                testedAt,
                result.outcome().name(),
                result.httpStatus(),
                detail
        );
        eventRecorder.recordTested(connection, result.outcome(), detail, actorId);

        return new PlatformConnectionTestResponse(
                connection.getId(),
                result.outcome(),
                result.httpStatus(),
                result.message(),
                testedAt,
                connection.getLastInboundOrderAt()
        );
    }

    private PlatformConnectionTestResponse awaitingInbound(PlatformConnection connection) {
        OffsetDateTime lastInboundOrderAt = connection.getLastInboundOrderAt();
        String message = lastInboundOrderAt == null
                ? "This channel only receives orders. No orders have arrived yet, so there is "
                        + "nothing to call. Waiting for the first inbound order."
                : "This channel only receives orders. Last order received "
                        + INBOUND_FORMAT.format(lastInboundOrderAt) + ".";

        return new PlatformConnectionTestResponse(
                connection.getId(),
                PlatformTestOutcome.AWAITING_INBOUND,
                null,
                message,
                null,
                lastInboundOrderAt
        );
    }

    private TestResult runTest(PlatformConnection connection) {
        try {
            if (!connection.isCredentialsConfigured()) {
                return new TestResult(
                        PlatformTestOutcome.NOT_CONFIGURED,
                        null,
                        "No API credential is saved for this connection. Add the credential and save."
                );
            }

            PlatformCredentials credentials = decryptCredentials(connection);
            PlatformOrderAdapter adapter = adapterFor(connection.getPlatformType());
            PlatformSyncTarget target = new PlatformSyncTarget(
                    connection.getId(),
                    connection.getPlatformType(),
                    connection.getApiEndpoint(),
                    connection.getEncryptedApiCredentials(),
                    connection.getLastSyncAt()
            );

            OutboundRequest request = adapter.buildTestRequest(target, credentials, httpTimeout);
            OutboundResponse response = outboundClient.execute(request);
            return classify(response.statusCode());
        } catch (PlatformOutboundException ex) {
            return new TestResult(PlatformTestOutcome.UNREACHABLE, null, ex.getMessage());
        } catch (PlatformSyncException | PlatformCredentialException ex) {
            return new TestResult(PlatformTestOutcome.NOT_CONFIGURED, null, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("Unexpected error while testing platform connection {}", connection.getId(), ex);
            return new TestResult(
                    PlatformTestOutcome.FAILED,
                    null,
                    "The connection test failed unexpectedly. Try again, then check the address and credential."
            );
        }
    }

    private TestResult classify(int statusCode) {
        if (statusCode >= 200 && statusCode < 300) {
            return new TestResult(
                    PlatformTestOutcome.SUCCESS,
                    statusCode,
                    "Connected successfully (HTTP " + statusCode + ")."
            );
        }
        if (statusCode == 401 || statusCode == 403) {
            return new TestResult(
                    PlatformTestOutcome.AUTHENTICATION_FAILED,
                    statusCode,
                    "The channel rejected the credentials (HTTP " + statusCode
                            + "). Check the token and save it again."
            );
        }
        if (statusCode == 429) {
            return new TestResult(
                    PlatformTestOutcome.FAILED,
                    statusCode,
                    "The channel is rate limiting requests (HTTP 429). Try again in a few minutes."
            );
        }
        if (statusCode == 404) {
            return new TestResult(
                    PlatformTestOutcome.FAILED,
                    statusCode,
                    "The API address was not found (HTTP 404). Check the address."
            );
        }
        if (statusCode >= 500) {
            return new TestResult(
                    PlatformTestOutcome.FAILED,
                    statusCode,
                    "The channel returned an error (HTTP " + statusCode + "). Try again later."
            );
        }
        return new TestResult(
                PlatformTestOutcome.FAILED,
                statusCode,
                "The channel rejected the request (HTTP " + statusCode + ")."
        );
    }

    private PlatformCredentials decryptCredentials(PlatformConnection connection) {
        try {
            String decrypted = platformCredentialCipher.decrypt(
                    connection.getEncryptedApiCredentials(),
                    API_CREDENTIALS_AAD
            );
            return platformCredentialsCodec.decode(decrypted);
        } catch (PlatformCredentialException ex) {
            throw new PlatformCredentialException(
                    "Stored platform credentials could not be read. Save the credential again."
            );
        }
    }

    private PlatformOrderAdapter adapterFor(PlatformType platformType) {
        return platformOrderAdapters.stream()
                .filter(adapter -> adapter.platformType() == platformType)
                .findFirst()
                .orElseThrow(() -> new PlatformSyncException(
                        "Testing is not supported for " + platformType + "."
                ));
    }

    private String truncate(String detail) {
        if (detail == null) {
            return null;
        }
        return detail.length() <= MAX_DETAIL_LENGTH
                ? detail
                : detail.substring(0, MAX_DETAIL_LENGTH);
    }

    private record TestResult(PlatformTestOutcome outcome, Integer httpStatus, String message) {
    }
}
