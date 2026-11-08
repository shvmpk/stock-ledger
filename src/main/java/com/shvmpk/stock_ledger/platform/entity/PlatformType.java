package com.shvmpk.stock_ledger.platform.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The single authoritative description of every sales channel StockLedger understands.
 *
 * <p>Display names, default webhook slugs, credential requirements and vendor signature
 * headers live here and nowhere else. Adding a channel means adding one constant: the
 * constructor refuses an incomplete or self-contradictory entry, and the catalogue endpoint,
 * the connection service and the inbound filter all read from this one declaration.</p>
 */
public enum PlatformType {

    SHOPIFY(
            "Shopify",
            true,
            true,
            "shopify",
            "https://your-store.myshopify.com/admin/api/2026-10",
            List.of(CredentialField.ACCESS_TOKEN),
            List.of("X-Shopify-Hmac-Sha256"),
            null
    ),
    WOOCOMMERCE(
            "WooCommerce",
            true,
            true,
            "woocommerce",
            "https://your-store.com/wp-json/wc/v3",
            List.of(CredentialField.CONSUMER_KEY, CredentialField.CONSUMER_SECRET),
            List.of("X-WC-Webhook-Signature"),
            null
    ),
    ETSY(
            "Etsy",
            false,
            false,
            "etsy",
            "https://openapi.etsy.com/v3/application",
            List.of(),
            List.of(),
            "Etsy orders cannot be imported yet: Etsy only delivers order notifications to "
                    + "approved applications, and that integration has not been built."
    ),
    TIKTOK_SHOP(
            "TikTok Shop",
            true,
            false,
            "tiktok-shop",
            "https://open-api.tiktokglobalshop.com",
            List.of(),
            List.of(),
            null
    ),
    AMAZON(
            "Amazon",
            true,
            false,
            "amazon",
            "https://sellingpartnerapi-na.amazon.com",
            List.of(),
            List.of(),
            null
    ),
    SQUARE(
            "Square POS",
            true,
            false,
            "square",
            "https://connect.squareup.com",
            List.of(),
            List.of("X-Square-HMAC-SHA256"),
            null
    ),
    CUSTOM_WEBHOOK(
            "Custom Channel",
            true,
            false,
            "custom-webhook",
            "https://your-automation.example.com/stockledger/orders",
            List.of(),
            List.of(),
            null
    );

    public static final String WEBHOOK_ROOT = "/api/v1/webhooks/orders/";

    private final String displayName;
    private final boolean supportsSetup;
    private final boolean supportsOutboundSync;
    private final String defaultSlug;
    private final String apiEndpointExample;
    private final List<CredentialField> credentialFields;
    private final List<String> signatureHeaders;
    private final String unsupportedReason;

    PlatformType(
            String displayName,
            boolean supportsSetup,
            boolean supportsOutboundSync,
            String defaultSlug,
            String apiEndpointExample,
            List<CredentialField> credentialFields,
            List<String> vendorSignatureHeaders,
            String unsupportedReason
    ) {
        if (supportsSetup == (unsupportedReason != null)) {
            throw new IllegalArgumentException(
                    "A platform either supports setup and carries no reason, or is unsupported and explains why: "
                            + name()
            );
        }
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.supportsSetup = supportsSetup;
        this.supportsOutboundSync = supportsOutboundSync;
        this.defaultSlug = Objects.requireNonNull(defaultSlug, "defaultSlug");
        this.apiEndpointExample = Objects.requireNonNull(apiEndpointExample, "apiEndpointExample");
        this.credentialFields = List.copyOf(credentialFields);
        this.signatureHeaders = signatureHeadersWithFallbacks(vendorSignatureHeaders);
        this.unsupportedReason = unsupportedReason;
    }

    /**
     * Built from literals rather than static constants on purpose: static fields are initialised
     * after the enum constants, so reading them here would see {@code null}.
     */
    private static List<String> signatureHeadersWithFallbacks(List<String> vendorSignatureHeaders) {
        List<String> headers = new ArrayList<>();
        headers.add("X-StockLedger-Signature");
        headers.addAll(vendorSignatureHeaders);
        headers.add("X-Webhook-Signature");
        return List.copyOf(headers);
    }

    public String displayName() {
        return displayName;
    }

    public boolean supportsSetup() {
        return supportsSetup;
    }

    public boolean supportsOutboundSync() {
        return supportsOutboundSync;
    }

    public String defaultSlug() {
        return defaultSlug;
    }

    /**
     * A realistic API address shown as the example in the setup form. It is
     * deliberately a pattern, not a working store, and carries no credentials.
     */
    public String apiEndpointExample() {
        return apiEndpointExample;
    }

    public List<CredentialField> credentialFields() {
        return credentialFields;
    }

    /**
     * Headers the inbound filter checks, in precedence order: our own universal header first,
     * then the vendor's, then the generic fallback.
     */
    public List<String> signatureHeaders() {
        return signatureHeaders;
    }

    /**
     * @return a plain-English explanation when {@link #supportsSetup()} is false, otherwise {@code null}
     */
    public String unsupportedReason() {
        return unsupportedReason;
    }

    public String webhookPath() {
        return WEBHOOK_ROOT + defaultSlug;
    }

    /**
     * The credential inputs a merchant must supply. The {@link #key} matches the field name
     * accepted by the connection API, so the setup form can be generated from the catalogue.
     */
    public enum CredentialField {
        ACCESS_TOKEN("accessToken", "Access token"),
        CONSUMER_KEY("consumerKey", "Consumer key"),
        CONSUMER_SECRET("consumerSecret", "Consumer secret");

        private final String key;
        private final String label;

        CredentialField(String key, String label) {
            this.key = key;
            this.label = label;
        }

        public String key() {
            return key;
        }

        public String label() {
            return label;
        }

        /**
         * Lower-case form used inside validation messages, e.g. "Shopify requires a non-blank access token."
         */
        public String described() {
            return label.toLowerCase(Locale.ROOT);
        }
    }
}
