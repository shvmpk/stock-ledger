package com.shvmpk.stock_ledger.unit.platform;

import com.shvmpk.stock_ledger.platform.entity.PlatformCredentials;
import com.shvmpk.stock_ledger.platform.security.PlatformCredentialException;
import com.shvmpk.stock_ledger.platform.security.PlatformCredentialsCodec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Platform Credentials Codec Unit Tests")
class PlatformCredentialsCodecTest {

    private final PlatformCredentialsCodec codec = new PlatformCredentialsCodec(new ObjectMapper());

    @Test
    @DisplayName("Should round-trip a Shopify access token and omit null fields")
    void shouldRoundTripShopifyCredentials() {
        PlatformCredentials credentials = new PlatformCredentials(
                "dummy-shopify-access-token",
                null,
                null
        );

        String json = codec.encode(credentials);

        assertThat(json).contains("\"accessToken\":\"dummy-shopify-access-token\"");
        assertThat(json).doesNotContain("consumerKey");
        assertThat(codec.decode(json)).isEqualTo(credentials);
    }

    @Test
    @DisplayName("Should round-trip WooCommerce consumer credentials")
    void shouldRoundTripWooCommerceCredentials() {
        PlatformCredentials credentials = new PlatformCredentials(
                null,
                "test-woocommerce-consumer-key",
                "dummy-woocommerce-secret"
        );

        String json = codec.encode(credentials);

        assertThat(json).contains("\"consumerKey\":\"test-woocommerce-consumer-key\"");
        assertThat(json).contains("\"consumerSecret\":\"dummy-woocommerce-secret\"");
        assertThat(codec.decode(json)).isEqualTo(credentials);
    }

    @Test
    @DisplayName("Should ignore unknown fields for forward compatibility")
    void shouldIgnoreUnknownFields() {
        String json = """
                {
                  "accessToken": "dummy-shopify-access-token",
                  "refreshToken": "shprt_legacy_5e8f1a7b2"
                }
                """;

        PlatformCredentials credentials = codec.decode(json);

        assertThat(credentials.accessToken()).isEqualTo("dummy-shopify-access-token");
        assertThat(credentials.consumerKey()).isNull();
        assertThat(credentials.consumerSecret()).isNull();
    }

    @Test
    @DisplayName("Should treat null and blank documents as an empty bundle")
    void shouldTreatAbsentDocumentAsEmpty() {
        assertThat(codec.decode(null)).isEqualTo(PlatformCredentials.empty());
        assertThat(codec.decode("   ")).isEqualTo(PlatformCredentials.empty());
    }

    @Test
    @DisplayName("Should encode an empty bundle as an empty JSON object")
    void shouldEncodeEmptyBundle() {
        assertThat(codec.encode(PlatformCredentials.empty())).isEqualTo("{}");
    }

    @Test
    @DisplayName("Should reject malformed JSON without leaking credential values")
    void shouldRejectMalformedJson() {
        assertThatThrownBy(() -> codec.decode("{not-json"))
                .isInstanceOf(PlatformCredentialException.class)
                .hasMessageContaining("could not be decoded")
                .hasMessageNotContaining("shpat_");
    }
}
