package com.shvmpk.stock_ledger.platform.security;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class PlatformCredentialCipher {

    private static final String VERSION = "v1";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int TAG_LENGTH_BYTES = TAG_LENGTH_BITS / 8;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom;

    public PlatformCredentialCipher(PlatformCredentialProperties properties) {
        this.key = new SecretKeySpec(properties.key(), "AES");
        this.secureRandom = new SecureRandom();
    }

    public String encrypt(String plaintext, String aad) {
        if (plaintext == null) {
            return null;
        }
        requireAad(aad);

        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            return VERSION + "." + encode(iv) + "." + encode(ciphertext);
        } catch (GeneralSecurityException ex) {
            throw new PlatformCredentialException("Platform credential encryption failed.");
        }
    }

    public String decrypt(String envelope, String aad) {
        if (envelope == null || envelope.isBlank()) {
            return null;
        }
        requireAad(aad);

        String[] parts = envelope.split("\\.", -1);
        if (parts.length != 3 || !VERSION.equals(parts[0])) {
            throw new PlatformCredentialException("Platform credential is malformed.");
        }

        try {
            byte[] iv = Base64.getUrlDecoder().decode(parts[1]);
            byte[] ciphertext = Base64.getUrlDecoder().decode(parts[2]);
            if (iv.length != IV_LENGTH_BYTES || ciphertext.length < TAG_LENGTH_BYTES) {
                throw new PlatformCredentialException("Platform credential is malformed.");
            }

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            byte[] plaintext = cipher.doFinal(ciphertext);

            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | GeneralSecurityException ex) {
            throw new PlatformCredentialException("Platform credential could not be decrypted.");
        }
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static void requireAad(String aad) {
        if (aad == null || aad.isBlank()) {
            throw new PlatformCredentialException("Platform credential context is missing.");
        }
    }
}
