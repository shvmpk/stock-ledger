package com.shvmpk.stock_ledger.unit.platform;

import com.shvmpk.stock_ledger.platform.entity.PlatformType;
import com.shvmpk.stock_ledger.platform.entity.PlatformType.CredentialField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Platform Catalogue Unit Tests")
class PlatformTypeTest {

    @Test
    @DisplayName("Should describe every platform completely so one file is the only place that knows it")
    void shouldDescribeEveryPlatformCompletely() {
        assertThat(PlatformType.values())
                .as("every supported sales channel is described exactly once")
                .hasSize(7);

        for (PlatformType platformType : PlatformType.values()) {
            assertThat(platformType.displayName())
                    .as("display name of %s", platformType)
                    .isNotBlank();
            assertThat(platformType.defaultSlug())
                    .as("default slug of %s", platformType)
                    .isNotBlank()
                    .matches("[a-z0-9]+(-[a-z0-9]+)*");
            assertThat(platformType.webhookPath())
                    .as("webhook path of %s", platformType)
                    .isEqualTo(PlatformType.WEBHOOK_ROOT + platformType.defaultSlug());
            assertThat(platformType.signatureHeaders())
                    .as("signature headers of %s", platformType)
                    .isNotEmpty()
                    .allSatisfy(header -> assertThat(header).isNotBlank());
        }
    }

    @Test
    @DisplayName("Should give every platform a unique name, slug and webhook path")
    void shouldNotDuplicatePlatformIdentity() {
        assertThat(Arrays.stream(PlatformType.values()).map(PlatformType::displayName).toList())
                .doesNotHaveDuplicates();
        assertThat(Arrays.stream(PlatformType.values()).map(PlatformType::defaultSlug).toList())
                .doesNotHaveDuplicates();
        assertThat(Arrays.stream(PlatformType.values()).map(PlatformType::webhookPath).toList())
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("Should explain every platform that cannot be set up, and only those")
    void shouldExplainOnlyUnsupportedPlatforms() {
        for (PlatformType platformType : PlatformType.values()) {
            if (platformType.supportsSetup()) {
                assertThat(platformType.unsupportedReason())
                        .as("unsupported reason for supported platform %s", platformType)
                        .isNull();
            } else {
                assertThat(platformType.unsupportedReason())
                        .as("unsupported reason for %s", platformType)
                        .isNotBlank();
            }
        }
    }

    @Test
    @DisplayName("Should list the credential fields each platform actually needs")
    void shouldListRequiredCredentialFields() {
        assertThat(PlatformType.SHOPIFY.credentialFields())
                .containsExactly(CredentialField.ACCESS_TOKEN);
        assertThat(PlatformType.WOOCOMMERCE.credentialFields())
                .containsExactly(CredentialField.CONSUMER_KEY, CredentialField.CONSUMER_SECRET);
        assertThat(PlatformType.CUSTOM_WEBHOOK.credentialFields()).isEmpty();
        assertThat(PlatformType.TIKTOK_SHOP.credentialFields()).isEmpty();
        assertThat(PlatformType.AMAZON.credentialFields()).isEmpty();
        assertThat(PlatformType.SQUARE.credentialFields()).isEmpty();
        assertThat(PlatformType.ETSY.credentialFields()).isEmpty();

        for (PlatformType platformType : PlatformType.values()) {
            if (platformType.supportsOutboundSync()) {
                assertThat(platformType.credentialFields())
                        .as("outbound sync for %s requires credentials", platformType)
                        .isNotEmpty();
            } else {
                assertThat(platformType.credentialFields())
                        .as("inbound-only %s takes orders without API credentials", platformType)
                        .isEmpty();
            }
        }
    }

    @Test
    @DisplayName("Should check our own signature header first, then the vendor header, then the generic one")
    void shouldExposeSignatureHeadersInPrecedenceOrder() {
        assertThat(PlatformType.SHOPIFY.signatureHeaders())
                .containsExactly("X-StockLedger-Signature", "X-Shopify-Hmac-Sha256", "X-Webhook-Signature");
        assertThat(PlatformType.WOOCOMMERCE.signatureHeaders())
                .contains("X-WC-Webhook-Signature");
        assertThat(PlatformType.SQUARE.signatureHeaders())
                .contains("X-Square-HMAC-SHA256");

        for (PlatformType platformType : PlatformType.values()) {
            List<String> headers = platformType.signatureHeaders();
            assertThat(headers.get(0))
                    .as("universal header first for %s", platformType)
                    .isEqualTo("X-StockLedger-Signature");
            assertThat(headers.get(headers.size() - 1))
                    .as("generic fallback last for %s", platformType)
                    .isEqualTo("X-Webhook-Signature");
        }
    }

    @Test
    @DisplayName("Should support set-up and outbound sync for Shopify and WooCommerce only")
    void shouldExposeCapabilities() {
        assertThat(PlatformType.SHOPIFY.supportsSetup()).isTrue();
        assertThat(PlatformType.SHOPIFY.supportsOutboundSync()).isTrue();
        assertThat(PlatformType.WOOCOMMERCE.supportsSetup()).isTrue();
        assertThat(PlatformType.WOOCOMMERCE.supportsOutboundSync()).isTrue();
        assertThat(PlatformType.ETSY.supportsSetup()).isFalse();
        assertThat(PlatformType.ETSY.supportsOutboundSync()).isFalse();
        assertThat(PlatformType.TIKTOK_SHOP.supportsSetup()).isTrue();
        assertThat(PlatformType.TIKTOK_SHOP.supportsOutboundSync()).isFalse();
        assertThat(PlatformType.CUSTOM_WEBHOOK.supportsSetup()).isTrue();
        assertThat(PlatformType.CUSTOM_WEBHOOK.supportsOutboundSync()).isFalse();
    }

    @Test
    @DisplayName("Should expose the inbound webhook path for each platform")
    void shouldExposeWebhookPaths() {
        assertThat(PlatformType.SHOPIFY.webhookPath())
                .isEqualTo("/api/v1/webhooks/orders/shopify");
        assertThat(PlatformType.WOOCOMMERCE.webhookPath())
                .isEqualTo("/api/v1/webhooks/orders/woocommerce");
        assertThat(PlatformType.ETSY.webhookPath())
                .isEqualTo("/api/v1/webhooks/orders/etsy");
        assertThat(PlatformType.CUSTOM_WEBHOOK.webhookPath())
                .isEqualTo("/api/v1/webhooks/orders/custom-webhook");
    }
}
