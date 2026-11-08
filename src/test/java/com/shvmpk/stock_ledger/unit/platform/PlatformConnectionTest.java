package com.shvmpk.stock_ledger.unit.platform;

import com.shvmpk.stock_ledger.platform.entity.PlatformConnection;
import com.shvmpk.stock_ledger.platform.entity.PlatformType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Platform Connection Entity Unit Tests")
class PlatformConnectionTest {

    @Test
    @DisplayName("Should start as an active PENDING connection with encrypted fields")
    void shouldCreatePendingConnection() {
        PlatformConnection connection = PlatformConnection.create(
                UUID.randomUUID(),
                PlatformType.SHOPIFY,
                "https://northwind-supply.myshopify.com",
                "v1.encrypted-webhook-secret",
                "v1.encrypted-api-credentials"
        );

        assertThat(connection.isActive()).isTrue();
        assertThat(connection.getSyncStatus()).isEqualTo("PENDING");
        assertThat(connection.getDeactivatedAt()).isNull();
        assertThat(connection.isWebhookConfigured()).isTrue();
        assertThat(connection.isCredentialsConfigured()).isTrue();
        assertThat(connection.getCreatedAt()).isNotNull();
        assertThat(connection.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should treat blank ciphertext as not configured")
    void shouldTreatBlankCiphertextAsNotConfigured() {
        PlatformConnection connection = PlatformConnection.create(
                UUID.randomUUID(),
                PlatformType.WOOCOMMERCE,
                "https://northwind-home.co.uk",
                "",
                null
        );

        assertThat(connection.isWebhookConfigured()).isFalse();
        assertThat(connection.isCredentialsConfigured()).isFalse();
    }

    @Test
    @DisplayName("Should deactivate idempotently and reactivate with a clean timestamp")
    void shouldDeactivateAndReactivate() {
        PlatformConnection connection = PlatformConnection.create(
                UUID.randomUUID(),
                PlatformType.SHOPIFY,
                "https://northwind-supply.myshopify.com",
                "v1.encrypted-webhook-secret",
                "v1.encrypted-api-credentials"
        );

        connection.pause("Store closed for a seasonal break.");
        OffsetDateTime firstDeactivation = connection.getDeactivatedAt();

        assertThat(connection.isActive()).isFalse();
        assertThat(firstDeactivation).isNotNull();
        assertThat(connection.getPauseReason()).isEqualTo("Store closed for a seasonal break.");

        connection.pause("A different reason must not replace the first one.");
        assertThat(connection.getDeactivatedAt()).isEqualTo(firstDeactivation);
        assertThat(connection.getPauseReason()).isEqualTo("Store closed for a seasonal break.");

        connection.activate();
        assertThat(connection.isActive()).isTrue();
        assertThat(connection.getDeactivatedAt()).isNull();
        assertThat(connection.getPauseReason()).isNull();
    }

    @Test
    @DisplayName("Should backdate last sync without changing status or error")
    void shouldBackdateLastSyncAt() {
        PlatformConnection connection = PlatformConnection.create(
                UUID.randomUUID(),
                PlatformType.WOOCOMMERCE,
                "https://northwind-home.co.uk/wp-json/wc/v3",
                "v1.encrypted-webhook-secret",
                "v1.encrypted-api-credentials"
        );
        connection.updateSyncStatus(
                "FAILED",
                "WooCommerce REST API returned HTTP 401: consumer key is invalid."
        );
        OffsetDateTime backdated = OffsetDateTime.now().minusDays(3);

        connection.backdateLastSyncAt(backdated);

        assertThat(connection.getLastSyncAt()).isEqualTo(backdated);
        assertThat(connection.getSyncStatus()).isEqualTo("FAILED");
        assertThat(connection.getLastErrorMessage()).contains("HTTP 401");
    }

    @Test
    @DisplayName("Should update endpoint and encrypted fields while preserving the type")
    void shouldUpdateEncryptedFields() {
        PlatformConnection connection = PlatformConnection.create(
                UUID.randomUUID(),
                PlatformType.SHOPIFY,
                "https://northwind-supply.myshopify.com",
                "v1.encrypted-webhook-secret",
                "v1.encrypted-api-credentials"
        );

        connection.updateEndpoint("https://northwind-supply.myshopify.com/admin");
        connection.updateWebhookSecret("v1.replacement-webhook-secret");
        connection.updateApiCredentials("v1.replacement-api-credentials");

        assertThat(connection.getApiEndpoint()).isEqualTo("https://northwind-supply.myshopify.com/admin");
        assertThat(connection.getWebhookSecretEncrypted()).isEqualTo("v1.replacement-webhook-secret");
        assertThat(connection.getEncryptedApiCredentials()).isEqualTo("v1.replacement-api-credentials");
        assertThat(connection.getPlatformType()).isEqualTo(PlatformType.SHOPIFY);
    }
}
