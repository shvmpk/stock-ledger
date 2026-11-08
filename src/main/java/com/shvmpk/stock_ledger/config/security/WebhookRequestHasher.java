package com.shvmpk.stock_ledger.config.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class WebhookRequestHasher {

    private WebhookRequestHasher() {
    }

    public static String sha256Hex(byte[] body) {
        if (body == null) {
            throw new IllegalArgumentException("Webhook body cannot be null");
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    public static String sha256Hex(String body) {
        if (body == null) {
            throw new IllegalArgumentException("Webhook body cannot be null");
        }
        return sha256Hex(body.getBytes(StandardCharsets.UTF_8));
    }
}
