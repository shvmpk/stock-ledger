package com.shvmpk.stock_ledger.platform.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlatformCredentials(
        String accessToken,
        String consumerKey,
        String consumerSecret
) {

    public static PlatformCredentials empty() {
        return new PlatformCredentials(null, null, null);
    }

    public PlatformCredentials merge(PlatformCredentials patch) {
        if (patch == null) {
            return this;
        }
        return new PlatformCredentials(
                patch.accessToken() != null ? patch.accessToken() : accessToken,
                patch.consumerKey() != null ? patch.consumerKey() : consumerKey,
                patch.consumerSecret() != null ? patch.consumerSecret() : consumerSecret
        );
    }

    public boolean hasAny() {
        return hasText(accessToken) || hasText(consumerKey) || hasText(consumerSecret);
    }

    public String valueFor(PlatformType.CredentialField field) {
        return switch (field) {
            case ACCESS_TOKEN -> accessToken;
            case CONSUMER_KEY -> consumerKey;
            case CONSUMER_SECRET -> consumerSecret;
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
