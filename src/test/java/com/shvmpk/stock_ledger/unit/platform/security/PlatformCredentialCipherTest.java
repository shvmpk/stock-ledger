package com.shvmpk.stock_ledger.unit.platform.security;

import com.shvmpk.stock_ledger.platform.security.PlatformCredentialCipher;
import com.shvmpk.stock_ledger.platform.security.PlatformCredentialException;
import com.shvmpk.stock_ledger.platform.security.PlatformCredentialProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Platform Credential Cipher Unit Tests")
class PlatformCredentialCipherTest {

    private static final String KEY = "bf932A02/VQNtKfYxSaasAJGsal1yBYmUMLpZI0jj04=";
    private static final String OTHER_KEY = "WiFDWltL2YAAm4yfOhcUUO05olbn/4HFzDeW4RL2Qwo=";
    private static final String WEBHOOK_AAD = "webhookSecret";
    private static final String API_AAD = "apiCredentials";
    private static final String SHOPIFY_TOKEN = "dummy-shopify-test-credential";
    private static final String WOOCOMMERCE_SECRET = "dummy-woocommerce-test-secret";

    private PlatformCredentialCipher cipher;

    @BeforeEach
    void setUp() {
        cipher = cipherWith(KEY);
    }

    @Test
    @DisplayName("Should round-trip a credential through a versioned AES-GCM envelope")
    void shouldRoundTrip() {
        String envelope = cipher.encrypt(SHOPIFY_TOKEN, API_AAD);

        assertThat(envelope).startsWith("v1.");
        assertThat(envelope.split("\\.")).hasSize(3);
        assertThat(envelope).doesNotContain(SHOPIFY_TOKEN);
        assertThat(cipher.decrypt(envelope, API_AAD)).isEqualTo(SHOPIFY_TOKEN);
    }

    @Test
    @DisplayName("Should use a fresh IV for every encryption")
    void shouldUseFreshIv() {
        String first = cipher.encrypt(SHOPIFY_TOKEN, API_AAD);
        String second = cipher.encrypt(SHOPIFY_TOKEN, API_AAD);

        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt(first, API_AAD)).isEqualTo(SHOPIFY_TOKEN);
        assertThat(cipher.decrypt(second, API_AAD)).isEqualTo(SHOPIFY_TOKEN);
    }

    @Test
    @DisplayName("Should reject tampered ciphertext")
    void shouldRejectTamperedCiphertext() {
        String envelope = cipher.encrypt(WOOCOMMERCE_SECRET, API_AAD);
        String[] parts = envelope.split("\\.");
        byte[] ciphertext = Base64.getUrlDecoder().decode(parts[2]);
        ciphertext[ciphertext.length - 1] ^= 0x01;
        String tampered = "v1." + parts[1] + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(ciphertext);

        assertThatThrownBy(() -> cipher.decrypt(tampered, API_AAD))
                .isInstanceOf(PlatformCredentialException.class)
                .hasMessageNotContaining(WOOCOMMERCE_SECRET)
                .hasMessageNotContaining(KEY);
    }

    @Test
    @DisplayName("Should bind ciphertext to its field-association AAD")
    void shouldRejectWrongAad() {
        String envelope = cipher.encrypt("dummy-webhook-test-secret", WEBHOOK_AAD);

        assertThatThrownBy(() -> cipher.decrypt(envelope, API_AAD))
                .isInstanceOf(PlatformCredentialException.class)
                .hasMessageNotContaining("whsec_");
    }

    @Test
    @DisplayName("Should reject decryption with the wrong key")
    void shouldRejectWrongKey() {
        String envelope = cipher.encrypt(SHOPIFY_TOKEN, API_AAD);
        PlatformCredentialCipher otherCipher = cipherWith(OTHER_KEY);

        assertThatThrownBy(() -> otherCipher.decrypt(envelope, API_AAD))
                .isInstanceOf(PlatformCredentialException.class)
                .hasMessageNotContaining(SHOPIFY_TOKEN)
                .hasMessageNotContaining(KEY)
                .hasMessageNotContaining(OTHER_KEY);
    }

    @Test
    @DisplayName("Should reject malformed, truncated, and unsupported envelopes")
    void shouldRejectMalformedEnvelopes() {
        String valid = cipher.encrypt(SHOPIFY_TOKEN, API_AAD);
        String truncated = valid.substring(0, valid.length() - 6);
        String validIv = valid.split("\\.")[1];

        assertThatThrownBy(() -> cipher.decrypt("not-an-envelope", API_AAD))
                .isInstanceOf(PlatformCredentialException.class);
        assertThatThrownBy(() -> cipher.decrypt("v2." + validIv + ".AAAA", API_AAD))
                .isInstanceOf(PlatformCredentialException.class);
        assertThatThrownBy(() -> cipher.decrypt("v1.%%%%.AAAA", API_AAD))
                .isInstanceOf(PlatformCredentialException.class);
        assertThatThrownBy(() -> cipher.decrypt("v1..", API_AAD))
                .isInstanceOf(PlatformCredentialException.class);
        assertThatThrownBy(() -> cipher.decrypt("v1.AAAA.AAAA", API_AAD))
                .isInstanceOf(PlatformCredentialException.class);
        assertThatThrownBy(() -> cipher.decrypt(truncated, API_AAD))
                .isInstanceOf(PlatformCredentialException.class);
    }

    @Test
    @DisplayName("Should return null for absent plaintext or ciphertext")
    void shouldHandleNullValues() {
        assertThat(cipher.encrypt(null, API_AAD)).isNull();
        assertThat(cipher.decrypt(null, API_AAD)).isNull();
        assertThat(cipher.decrypt("   ", API_AAD)).isNull();
    }

    @Test
    @DisplayName("Should encrypt an empty string without collapsing it to null")
    void shouldEncryptEmptyString() {
        String envelope = cipher.encrypt("", API_AAD);

        assertThat(envelope).isNotNull();
        assertThat(cipher.decrypt(envelope, API_AAD)).isEmpty();
    }

    private PlatformCredentialCipher cipherWith(String key) {
        return new PlatformCredentialCipher(new PlatformCredentialProperties(key, false));
    }
}
