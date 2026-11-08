package com.shvmpk.stock_ledger.unit.platform.security;

import com.shvmpk.stock_ledger.platform.security.PlatformCredentialProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Platform Credential Properties Unit Tests")
class PlatformCredentialPropertiesTest {

    private static final String VALID_KEY = "bf932A02/VQNtKfYxSaasAJGsal1yBYmUMLpZI0jj04=";

    @Test
    @DisplayName("Should decode a base64 32-byte key and read the endpoint policy")
    void shouldDecodeValidKey() {
        PlatformCredentialProperties properties = new PlatformCredentialProperties(VALID_KEY, true);

        assertThat(properties.key()).hasSize(32);
        assertThat(properties.allowInsecureEndpoints()).isTrue();
    }

    @Test
    @DisplayName("Should fail fast when the key is missing or blank")
    void shouldRejectMissingKey() {
        assertThatThrownBy(() -> new PlatformCredentialProperties(null, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.platform.credentials-key");

        assertThatThrownBy(() -> new PlatformCredentialProperties("   ", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.platform.credentials-key");
    }

    @Test
    @DisplayName("Should reject a key that is not valid base64")
    void shouldRejectNonBase64Key() {
        assertThatThrownBy(() -> new PlatformCredentialProperties("%%%not-base64%%%", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.platform.credentials-key")
                .hasMessageNotContaining("%%%not-base64%%%");
    }

    @Test
    @DisplayName("Should reject keys that decode to 31 or 33 bytes")
    void shouldRejectWrongLengthKey() {
        String key31 = Base64.getEncoder().encodeToString(new byte[31]);
        String key33 = Base64.getEncoder().encodeToString(new byte[33]);

        assertThatThrownBy(() -> new PlatformCredentialProperties(key31, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes")
                .hasMessageNotContaining(key31);

        assertThatThrownBy(() -> new PlatformCredentialProperties(key33, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes")
                .hasMessageNotContaining(key33);
    }

    @Test
    @DisplayName("Should default insecure endpoints to false")
    void shouldDefaultInsecureEndpointsToFalse() {
        PlatformCredentialProperties properties = new PlatformCredentialProperties(VALID_KEY, false);

        assertThat(properties.allowInsecureEndpoints()).isFalse();
    }
}
