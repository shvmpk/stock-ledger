package com.shvmpk.stock_ledger.platform.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Base64;

@Component
public class PlatformCredentialProperties {

    private static final int KEY_LENGTH_BYTES = 32;

    private final byte[] key;
    private final boolean allowInsecureEndpoints;

    public PlatformCredentialProperties(
            @Value("${app.platform.credentials-key:}") String credentialsKey,
            @Value("${app.platform.allow-insecure-endpoints:false}") boolean allowInsecureEndpoints
    ) {
        this.key = decodeKey(credentialsKey);
        this.allowInsecureEndpoints = allowInsecureEndpoints;
    }

    public byte[] key() {
        return key.clone();
    }

    public boolean allowInsecureEndpoints() {
        return allowInsecureEndpoints;
    }

    private static byte[] decodeKey(String credentialsKey) {
        if (credentialsKey == null || credentialsKey.isBlank()) {
            throw new IllegalStateException(
                    "app.platform.credentials-key must be set to a base64-encoded 32-byte key."
            );
        }

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(credentialsKey.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "app.platform.credentials-key must be valid base64."
            );
        }

        if (decoded.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException(
                    "app.platform.credentials-key must decode to exactly 32 bytes."
            );
        }

        return decoded;
    }
}
