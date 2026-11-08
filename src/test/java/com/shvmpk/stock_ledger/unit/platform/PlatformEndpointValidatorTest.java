package com.shvmpk.stock_ledger.unit.platform;

import com.shvmpk.stock_ledger.common.error.InvalidPlatformEndpointException;
import com.shvmpk.stock_ledger.platform.security.PlatformCredentialProperties;
import com.shvmpk.stock_ledger.platform.security.PlatformEndpointValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Platform Endpoint Validator Unit Tests")
class PlatformEndpointValidatorTest {

    private static final String KEY = "bf932A02/VQNtKfYxSaasAJGsal1yBYmUMLpZI0jj04=";

    @Test
    @DisplayName("Should accept and preserve a valid HTTPS endpoint")
    void shouldAcceptValidHttpsEndpoint() {
        PlatformEndpointValidator validator = validator(false);

        assertThat(validator.validateAndNormalize("https://northwind-supply.myshopify.com"))
                .isEqualTo("https://northwind-supply.myshopify.com");
        assertThat(validator.validateAndNormalize("https://northwind-supply.myshopify.com/admin/api/2026-07"))
                .isEqualTo("https://northwind-supply.myshopify.com/admin/api/2026-07");
    }

    @Test
    @DisplayName("Should normalize trailing slashes away")
    void shouldNormalizeTrailingSlashes() {
        PlatformEndpointValidator validator = validator(false);

        assertThat(validator.validateAndNormalize("https://northwind-supply.myshopify.com/"))
                .isEqualTo("https://northwind-supply.myshopify.com");
        assertThat(validator.validateAndNormalize("https://northwind-home.co.uk/api/"))
                .isEqualTo("https://northwind-home.co.uk/api");
    }

    @Test
    @DisplayName("Should allow HTTP and loopback endpoints in dev/test")
    void shouldAllowInsecureDevEndpoints() {
        PlatformEndpointValidator validator = validator(true);

        assertThat(validator.validateAndNormalize("http://localhost:8080"))
                .isEqualTo("http://localhost:8080");
        assertThat(validator.validateAndNormalize("http://127.0.0.1:8080/"))
                .isEqualTo("http://127.0.0.1:8080");
        assertThat(validator.validateAndNormalize("http://192.168.1.20:8080"))
                .isEqualTo("http://192.168.1.20:8080");
    }

    @Test
    @DisplayName("Should reject HTTP in production")
    void shouldRejectHttpInProduction() {
        assertInvalid(validator(false), "http://northwind-supply.myshopify.com");
    }

    @Test
    @DisplayName("Should reject non-HTTP schemes and relative URLs")
    void shouldRejectUnsupportedSchemesAndRelativeUrls() {
        PlatformEndpointValidator validator = validator(false);

        assertInvalid(validator, "ftp://northwind-supply.myshopify.com");
        assertInvalid(validator, "/api/v1/orders");
        assertInvalid(validator, "northwind-supply.myshopify.com");
    }

    @Test
    @DisplayName("Should reject endpoints without a host")
    void shouldRejectMissingHost() {
        assertInvalid(validator(false), "https:///orders");
        assertInvalid(validator(false), "https://");
    }

    @Test
    @DisplayName("Should reject userinfo, query strings, and fragments")
    void shouldRejectUnsafeUrlComponents() {
        PlatformEndpointValidator validator = validator(false);

        assertInvalid(validator, "https://merchant:whsec_secret@northwind-supply.myshopify.com");
        assertInvalid(validator, "https://northwind-supply.myshopify.com?shop=123");
        assertInvalid(validator, "https://northwind-supply.myshopify.com#section");
        assertInvalid(validator, "https://northwind-home.co.uk/api?token=shpat_secret");
    }

    @Test
    @DisplayName("Should reject literal loopback, private, link-local, and multicast addresses in production")
    void shouldRejectPrivateNetworkLiteralsInProduction() {
        PlatformEndpointValidator validator = validator(false);

        assertInvalid(validator, "https://localhost");
        assertInvalid(validator, "https://127.0.0.1");
        assertInvalid(validator, "https://127.10.20.30:8443");
        assertInvalid(validator, "https://0.0.0.0");
        assertInvalid(validator, "https://10.20.30.40");
        assertInvalid(validator, "https://172.16.4.10");
        assertInvalid(validator, "https://192.168.0.25");
        assertInvalid(validator, "https://169.254.10.20");
        assertInvalid(validator, "https://224.0.0.1");
        assertInvalid(validator, "https://[::1]");
        assertInvalid(validator, "https://[fc00::1]");
        assertInvalid(validator, "https://[fe80::1]");
    }

    @Test
    @DisplayName("Should reject blank and malformed endpoints")
    void shouldRejectBlankAndMalformedEndpoints() {
        PlatformEndpointValidator validator = validator(false);

        assertInvalid(validator, null);
        assertInvalid(validator, "");
        assertInvalid(validator, "   ");
        assertInvalid(validator, "https://northwind supply.myshopify.com");
    }

    private PlatformEndpointValidator validator(boolean allowInsecureEndpoints) {
        return new PlatformEndpointValidator(
                new PlatformCredentialProperties(KEY, allowInsecureEndpoints)
        );
    }

    private void assertInvalid(PlatformEndpointValidator validator, String endpoint) {
        assertThatThrownBy(() -> validator.validateAndNormalize(endpoint))
                .isInstanceOf(InvalidPlatformEndpointException.class)
                .extracting("errorCode")
                .isEqualTo("INVALID_PLATFORM_ENDPOINT");
    }
}
