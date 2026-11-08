package com.shvmpk.stock_ledger.platform.service;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Component
public class HmacSignatureValidator {

    public boolean isValid(byte[] payload, String secret, String providedSignature) {
        if (payload == null || secret == null || providedSignature == null || providedSignature.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String calculated = Base64.getEncoder().encodeToString(mac.doFinal(payload));
            return MessageDigest.isEqual(
                    calculated.getBytes(StandardCharsets.UTF_8),
                    providedSignature.trim().getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            return false;
        }
    }
}
