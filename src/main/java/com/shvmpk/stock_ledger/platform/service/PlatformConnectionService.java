package com.shvmpk.stock_ledger.platform.service;

import com.shvmpk.stock_ledger.common.error.ConcurrentChangeException;
import com.shvmpk.stock_ledger.common.error.InvalidIdempotencyKeyException;
import com.shvmpk.stock_ledger.common.error.InvalidPlatformCredentialsException;
import com.shvmpk.stock_ledger.common.error.InvalidPlatformVersionException;
import com.shvmpk.stock_ledger.common.error.NoPlatformChangesException;
import com.shvmpk.stock_ledger.common.error.PauseReasonRequiredException;
import com.shvmpk.stock_ledger.common.error.PlatformWebhookAddressInUseException;
import com.shvmpk.stock_ledger.common.error.ResourceNotFoundException;
import com.shvmpk.stock_ledger.common.error.UnsupportedPlatformTypeException;
import com.shvmpk.stock_ledger.platform.dto.CreatePlatformConnectionRequest;
import com.shvmpk.stock_ledger.platform.dto.PlatformConnectionResponse;
import com.shvmpk.stock_ledger.platform.dto.UpdatePlatformConnectionRequest;
import com.shvmpk.stock_ledger.platform.entity.PlatformConnection;
import com.shvmpk.stock_ledger.platform.entity.PlatformCredentials;
import com.shvmpk.stock_ledger.platform.entity.PlatformType;
import com.shvmpk.stock_ledger.platform.repository.PlatformConnectionCreationLock;
import com.shvmpk.stock_ledger.platform.repository.PlatformConnectionRepository;
import com.shvmpk.stock_ledger.platform.security.PlatformCredentialCipher;
import com.shvmpk.stock_ledger.platform.security.PlatformCredentialException;
import com.shvmpk.stock_ledger.platform.security.PlatformCredentialsCodec;
import com.shvmpk.stock_ledger.platform.security.PlatformEndpointValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformConnectionService {

    private static final int WEBHOOK_SECRET_MIN_LENGTH = 16;
    private static final int WEBHOOK_SECRET_MAX_LENGTH = 512;
    private static final String WEBHOOK_SECRET_AAD = "webhookSecret";
    private static final String API_CREDENTIALS_AAD = "apiCredentials";
    private static final Pattern IDEMPOTENCY_KEY_PATTERN =
            Pattern.compile("^[A-Za-z0-9_-]{8,100}$");

    private final PlatformConnectionRepository connectionRepository;
    private final PlatformConnectionCreationLock creationRequestLock;
    private final PlatformConnectionEventRecorder eventRecorder;
    private final PlatformCredentialCipher credentialCipher;
    private final PlatformCredentialsCodec credentialsCodec;
    private final PlatformEndpointValidator endpointValidator;

    @Transactional(readOnly = true)
    public List<PlatformConnectionResponse> list(Boolean active) {
        List<PlatformConnection> connections = active == null
                ? connectionRepository.findAllByOrderByActiveDescPlatformTypeAsc()
                : connectionRepository.findByActive(active).stream()
                        .sorted(Comparator.comparing(PlatformConnection::getPlatformType))
                        .toList();

        return connections.stream()
                .map(PlatformConnectionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PlatformConnectionResponse get(UUID id) {
        return PlatformConnectionResponse.from(findConnection(id));
    }

    @Transactional
    public PlatformConnectionResponse create(CreatePlatformConnectionRequest request, UUID actorId) {
        return create(request, actorId, null).connection();
    }

    /**
     * Creates a connection, or returns the one a previous submission with the
     * same request key already created. The key is optional so existing API
     * callers keep working; the interface always sends one.
     */
    @Transactional
    public PlatformConnectionCreationResult create(
            CreatePlatformConnectionRequest request,
            UUID actorId,
            String idempotencyKey
    ) {
        String requestKey = normalizeIdempotencyKey(idempotencyKey);
        if (requestKey != null) {
            creationRequestLock.acquire(requestKey);
            Optional<PlatformConnection> replay =
                    connectionRepository.findByCreationRequestKey(requestKey);
            if (replay.isPresent()) {
                log.info(
                        "Replayed the create of platform connection {} for request key {}.",
                        replay.get().getId(),
                        requestKey
                );
                return new PlatformConnectionCreationResult(
                        PlatformConnectionResponse.from(replay.get()),
                        true
                );
            }
        }

        PlatformType platformType = request.platformType();
        requireSetupSupport(platformType);

        String endpoint = endpointValidator.validateAndNormalize(request.apiEndpoint());
        String providedDisplayName = request.displayName() != null && !request.displayName().isBlank()
                ? request.displayName().trim()
                : null;
        String displayName = providedDisplayName != null ? providedDisplayName : platformType.displayName();
        String slug = request.slug() != null && !request.slug().isBlank()
                ? request.slug().trim().toLowerCase()
                : providedDisplayName != null
                        ? PlatformConnection.slugify(providedDisplayName)
                        : platformType.defaultSlug();

        if (connectionRepository.findBySlugAndActiveTrue(slug).isPresent()) {
            throw new PlatformWebhookAddressInUseException(slug);
        }

        validateWebhookSecret(request.webhookSecret());
        validateProvidedCredentialFields(
                request.accessToken(),
                request.consumerKey(),
                request.consumerSecret()
        );
        PlatformCredentials credentials = new PlatformCredentials(
                request.accessToken(),
                request.consumerKey(),
                request.consumerSecret()
        );
        validatePlatformCredentials(platformType, credentials);

        PlatformConnection connection = PlatformConnection.create(
                UUID.randomUUID(),
                platformType,
                displayName,
                slug,
                endpoint,
                credentialCipher.encrypt(request.webhookSecret(), WEBHOOK_SECRET_AAD),
                hasCredentials(credentials) ? credentialCipher.encrypt(credentialsCodec.encode(credentials), API_CREDENTIALS_AAD) : null,
                requestKey
        );

        try {
            connectionRepository.saveAndFlush(connection);
        } catch (DataIntegrityViolationException ex) {
            log.warn(
                    "Could not create {} platform connection: the webhook address '{}' is already in use.",
                    platformType,
                    slug,
                    ex
            );
            throw new PlatformWebhookAddressInUseException(slug);
        }

        eventRecorder.recordCreated(connection, actorId);

        log.info(
                "Created {} platform connection {} by actor {}; fields=platformType,displayName,slug,apiEndpoint,webhookSecret,apiCredentials",
                platformType,
                connection.getId(),
                actorId
        );
        return new PlatformConnectionCreationResult(
                PlatformConnectionResponse.from(connection),
                false
        );
    }

    @Transactional
    public PlatformConnectionResponse update(UUID id, UpdatePlatformConnectionRequest request, UUID actorId) {
        PlatformConnection connection = findConnection(id);
        requireSetupSupport(connection.getPlatformType());

        Long expectedVersion = request.version();
        if (expectedVersion == null || expectedVersion < 0) {
            throw new InvalidPlatformVersionException();
        }
        if (expectedVersion != connection.getVersion()) {
            throw new ConcurrentChangeException();
        }

        if (!request.hasAnyField()) {
            throw new NoPlatformChangesException();
        }

        Set<String> changedFields = new LinkedHashSet<>();

        if (request.displayName() != null) {
            String displayName = request.displayName().trim();
            if (!displayName.equals(connection.getDisplayName())) {
                connection.updateDisplayName(displayName);
                changedFields.add("displayName");
            }
        }

        if (request.apiEndpoint() != null) {
            String endpoint = endpointValidator.validateAndNormalize(request.apiEndpoint());
            if (!endpoint.equals(connection.getApiEndpoint())) {
                connection.updateEndpoint(endpoint);
                changedFields.add("apiEndpoint");
            }
        }

        if (request.active() != null) {
            boolean wasActive = connection.isActive();
            if (request.active()) {
                if (connectionRepository.existsBySlugAndActiveTrueAndIdNot(
                        connection.getSlug(),
                        connection.getId()
                )) {
                    throw new PlatformWebhookAddressInUseException(connection.getSlug());
                }
                connection.activate();
            } else if (wasActive) {
                String pauseReason = requirePauseReason(request.pauseReason());
                connection.pause(pauseReason);
            }
            if (connection.isActive() != wasActive) {
                changedFields.add("active");
            }
        }

        if (request.webhookSecret() != null) {
            validateWebhookSecret(request.webhookSecret());
            connection.updateWebhookSecret(
                    credentialCipher.encrypt(request.webhookSecret(), WEBHOOK_SECRET_AAD)
            );
            changedFields.add("webhookSecret");
        }

        validateProvidedCredentialFields(
                request.accessToken(),
                request.consumerKey(),
                request.consumerSecret()
        );
        PlatformCredentials existingCredentials = decryptCredentials(connection);
        PlatformCredentials mergedCredentials = existingCredentials.merge(new PlatformCredentials(
                request.accessToken(),
                request.consumerKey(),
                request.consumerSecret()
        ));
        validatePlatformCredentials(connection.getPlatformType(), mergedCredentials);

        if (!mergedCredentials.equals(existingCredentials)) {
            connection.updateApiCredentials(
                    credentialCipher.encrypt(credentialsCodec.encode(mergedCredentials), API_CREDENTIALS_AAD)
            );
            changedFields.add("apiCredentials");
        }

        try {
            connectionRepository.saveAndFlush(connection);
        } catch (OptimisticLockingFailureException ex) {
            log.info(
                    "Refused a stale save of platform connection {} by actor {}.",
                    connection.getId(),
                    actorId
            );
            throw new ConcurrentChangeException();
        } catch (DataIntegrityViolationException ex) {
            log.warn(
                    "Could not update {} platform connection {}: the webhook address '{}' is already in use.",
                    connection.getPlatformType(),
                    connection.getId(),
                    connection.getSlug(),
                    ex
            );
            throw new PlatformWebhookAddressInUseException(connection.getSlug());
        }

        recordChange(connection, changedFields, request.pauseReason(), actorId);

        log.info(
                "Updated {} platform connection {} by actor {}; fields={}",
                connection.getPlatformType(),
                connection.getId(),
                actorId,
                changedFields
        );
        return PlatformConnectionResponse.from(connection);
    }

    @Transactional
    public PlatformConnectionResponse deactivate(UUID id, String reason, UUID actorId) {
        PlatformConnection connection = findConnection(id);
        requireSetupSupport(connection.getPlatformType());

        if (!connection.isActive()) {
            return PlatformConnectionResponse.from(connection);
        }

        String pauseReason = requirePauseReason(reason);
        connection.pause(pauseReason);
        try {
            connectionRepository.saveAndFlush(connection);
        } catch (OptimisticLockingFailureException ex) {
            log.info(
                    "Refused a stale pause of platform connection {} by actor {}.",
                    connection.getId(),
                    actorId
            );
            throw new ConcurrentChangeException();
        }

        eventRecorder.recordPaused(connection, pauseReason, actorId);

        log.info(
                "Deactivated {} platform connection {} by actor {}; reason={}",
                connection.getPlatformType(),
                connection.getId(),
                actorId,
                pauseReason
        );
        return PlatformConnectionResponse.from(connection);
    }

    private PlatformConnection findConnection(UUID id) {
        return connectionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PlatformConnection", id));
    }

    private void recordChange(
            PlatformConnection connection,
            Set<String> changedFields,
            String pauseReason,
            UUID actorId
    ) {
        if (changedFields.isEmpty()) {
            return;
        }
        if (changedFields.size() == 1 && changedFields.contains("active")) {
            if (connection.isActive()) {
                eventRecorder.recordResumed(connection, actorId);
            } else {
                eventRecorder.recordPaused(
                        connection,
                        pauseReason == null ? null : pauseReason.trim(),
                        actorId
                );
            }
            return;
        }
        eventRecorder.recordUpdated(connection, changedFields, actorId);
    }

    private String requirePauseReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new PauseReasonRequiredException();
        }
        return reason.trim();
    }

    private void requireSetupSupport(PlatformType platformType) {
        if (platformType == null || !platformType.supportsSetup()) {
            throw new UnsupportedPlatformTypeException(platformType);
        }
    }

    private void validateWebhookSecret(String webhookSecret) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new InvalidPlatformCredentialsException("Webhook secret must not be blank.");
        }
        if (webhookSecret.length() < WEBHOOK_SECRET_MIN_LENGTH
                || webhookSecret.length() > WEBHOOK_SECRET_MAX_LENGTH) {
            throw new InvalidPlatformCredentialsException(
                    "Webhook secret must be between 16 and 512 characters."
            );
        }
    }

    private void validateProvidedCredentialFields(
            String accessToken,
            String consumerKey,
            String consumerSecret
    ) {
        if (accessToken != null && accessToken.isBlank()) {
            throw new InvalidPlatformCredentialsException("Access token must not be blank.");
        }
        if (consumerKey != null && consumerKey.isBlank()) {
            throw new InvalidPlatformCredentialsException("Consumer key must not be blank.");
        }
        if (consumerSecret != null && consumerSecret.isBlank()) {
            throw new InvalidPlatformCredentialsException("Consumer secret must not be blank.");
        }
    }

    private void validatePlatformCredentials(PlatformType platformType, PlatformCredentials credentials) {
        for (PlatformType.CredentialField field : platformType.credentialFields()) {
            if (!hasText(credentials.valueFor(field))) {
                throw new InvalidPlatformCredentialsException(
                        platformType.displayName() + " requires a non-blank " + field.described() + "."
                );
            }
        }
    }

    private boolean hasCredentials(PlatformCredentials credentials) {
        return hasText(credentials.accessToken()) || hasText(credentials.consumerKey()) || hasText(credentials.consumerSecret());
    }

    private PlatformCredentials decryptCredentials(PlatformConnection connection) {
        try {
            if (connection.getEncryptedApiCredentials() == null) {
                return PlatformCredentials.empty();
            }
            return credentialsCodec.decode(
                    credentialCipher.decrypt(connection.getEncryptedApiCredentials(), API_CREDENTIALS_AAD)
            );
        } catch (PlatformCredentialException ex) {
            log.warn(
                    "Could not decrypt API credentials for platform connection {}.",
                    connection.getId()
            );
            throw ex;
        }
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null) {
            return null;
        }
        String trimmed = idempotencyKey.trim();
        if (!IDEMPOTENCY_KEY_PATTERN.matcher(trimmed).matches()) {
            throw new InvalidIdempotencyKeyException();
        }
        return trimmed;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
