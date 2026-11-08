package com.shvmpk.stock_ledger.platform.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "platform_connections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlatformConnection {

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    /**
     * Incremented by Hibernate on every save. Clients send the version they
     * read so a save built on older data is refused instead of overwriting it.
     */
    @Version
    @Column(nullable = false)
    private long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform_type", nullable = false, length = 50)
    private PlatformType platformType;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "slug", length = 100)
    private String slug;

    @Column(name = "api_endpoint", nullable = false)
    private String apiEndpoint;

    @Column(name = "webhook_secret_encrypted", nullable = false, columnDefinition = "TEXT")
    private String webhookSecretEncrypted;

    @Column(name = "encrypted_api_credentials", columnDefinition = "TEXT")
    private String encryptedApiCredentials;

    @Column(name = "last_sync_at")
    private OffsetDateTime lastSyncAt;

    @Column(name = "sync_status", nullable = false, length = 50)
    private String syncStatus;

    @Column(name = "last_error_message", columnDefinition = "TEXT")
    private String lastErrorMessage;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "deactivated_at")
    private OffsetDateTime deactivatedAt;

    /**
     * Why the connection is paused. It is shown on the card while the
     * connection is inactive and cleared when it resumes; the history keeps
     * the reason that belonged to each pause.
     */
    @Column(name = "pause_reason", length = 500)
    private String pauseReason;

    /**
     * The most recent connection test. These fields are written with a native
     * update so a test never bumps {@code version} and never invalidates an
     * edit the owner already has open.
     */
    @Column(name = "last_tested_at")
    private OffsetDateTime lastTestedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_test_outcome", length = 40)
    private PlatformTestOutcome lastTestOutcome;

    @Column(name = "last_test_http_status")
    private Integer lastTestHttpStatus;

    @Column(name = "last_test_detail", length = 500)
    private String lastTestDetail;

    /**
     * When a genuinely new inbound webhook order was last accepted. A replay
     * does not move it. Written with a native update, so it does not bump
     * {@code version} either.
     */
    @Column(name = "last_inbound_order_at")
    private OffsetDateTime lastInboundOrderAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * The request key that created this connection, when the caller supplied
     * one. A retry with the same key finds this row instead of creating a
     * second connection. Never exposed by the API.
     */
    @Column(name = "creation_request_key", length = 100, updatable = false)
    private String creationRequestKey;

    public static PlatformConnection create(
            UUID id,
            PlatformType platformType,
            String apiEndpoint,
            String webhookSecretEncrypted,
            String encryptedApiCredentials
    ) {
        return create(id, platformType, platformType.displayName(), platformType.defaultSlug(), apiEndpoint, webhookSecretEncrypted, encryptedApiCredentials);
    }

    public static PlatformConnection create(
            UUID id,
            PlatformType platformType,
            String displayName,
            String slug,
            String apiEndpoint,
            String webhookSecretEncrypted,
            String encryptedApiCredentials
    ) {
        return create(
                id,
                platformType,
                displayName,
                slug,
                apiEndpoint,
                webhookSecretEncrypted,
                encryptedApiCredentials,
                null
        );
    }

    public static PlatformConnection create(
            UUID id,
            PlatformType platformType,
            String displayName,
            String slug,
            String apiEndpoint,
            String webhookSecretEncrypted,
            String encryptedApiCredentials,
            String creationRequestKey
    ) {
        PlatformConnection conn = new PlatformConnection();
        conn.id = Objects.requireNonNullElseGet(id, UUID::randomUUID);
        conn.platformType = Objects.requireNonNull(platformType, "PlatformType cannot be null");
        conn.displayName = displayName != null && !displayName.isBlank() ? displayName : platformType.displayName();
        conn.slug = slug != null && !slug.isBlank()
                ? slug.trim().toLowerCase()
                : displayName != null && !displayName.isBlank()
                        ? slugify(displayName)
                        : platformType.defaultSlug();
        conn.apiEndpoint = Objects.requireNonNull(apiEndpoint, "ApiEndpoint cannot be null");
        conn.webhookSecretEncrypted = Objects.requireNonNull(
                webhookSecretEncrypted,
                "WebhookSecretEncrypted cannot be null"
        );
        conn.encryptedApiCredentials = encryptedApiCredentials;
        conn.syncStatus = "PENDING";
        conn.active = true;
        conn.createdAt = OffsetDateTime.now();
        conn.updatedAt = OffsetDateTime.now();
        conn.creationRequestKey = creationRequestKey;
        return conn;
    }

    /**
     * The single rule that turns a merchant-facing name into a webhook address segment.
     * Used both when saving a connection and when adding a new one, so the two cannot drift.
     */
    public static String slugify(String value) {
        return NON_ALPHANUMERIC.matcher(value.trim().toLowerCase()).replaceAll("-").replaceAll("^-|-$", "");
    }

    /**
     * Renames a connection without touching its webhook address. The address is
     * the identity of the connection and is immutable after creation.
     */
    public void updateDisplayName(String displayName) {
        this.displayName = Objects.requireNonNull(displayName, "DisplayName cannot be null").trim();
        this.updatedAt = OffsetDateTime.now();
    }

    public void updateEndpoint(String apiEndpoint) {
        this.apiEndpoint = Objects.requireNonNull(apiEndpoint, "ApiEndpoint cannot be null");
        this.updatedAt = OffsetDateTime.now();
    }

    public void updateWebhookSecret(String webhookSecretEncrypted) {
        this.webhookSecretEncrypted = Objects.requireNonNull(
                webhookSecretEncrypted,
                "WebhookSecretEncrypted cannot be null"
        );
        this.updatedAt = OffsetDateTime.now();
    }

    public void updateApiCredentials(String encryptedApiCredentials) {
        this.encryptedApiCredentials = encryptedApiCredentials;
        this.updatedAt = OffsetDateTime.now();
    }

    public void activate() {
        if (!active) {
            this.active = true;
            this.deactivatedAt = null;
            this.pauseReason = null;
        }
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * Pauses the connection and records why. Pausing an already paused
     * connection keeps the first reason and the first deactivation time.
     */
    public void pause(String reason) {
        if (active) {
            this.active = false;
            this.deactivatedAt = OffsetDateTime.now();
            this.pauseReason = Objects.requireNonNull(reason, "Pause reason cannot be null").trim();
        }
        this.updatedAt = OffsetDateTime.now();
    }

    public boolean isWebhookConfigured() {
        return webhookSecretEncrypted != null && !webhookSecretEncrypted.isBlank();
    }

    public boolean isCredentialsConfigured() {
        return encryptedApiCredentials != null && !encryptedApiCredentials.isBlank();
    }

    public void updateSyncStatus(String status, String errorMessage) {
        this.syncStatus = status;
        this.lastErrorMessage = errorMessage;
        this.lastSyncAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    public void backdateLastSyncAt(OffsetDateTime lastSyncAt) {
        this.lastSyncAt = Objects.requireNonNull(lastSyncAt, "LastSyncAt cannot be null");
        this.updatedAt = OffsetDateTime.now();
    }
}
