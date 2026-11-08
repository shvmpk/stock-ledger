package com.shvmpk.stock_ledger.unit.platform.outbound;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.shvmpk.stock_ledger.platform.outbound.HardenedOutboundClient;
import com.shvmpk.stock_ledger.platform.outbound.OutboundAddressPolicy;
import com.shvmpk.stock_ledger.platform.outbound.OutboundRequest;
import com.shvmpk.stock_ledger.platform.outbound.OutboundResponse;
import com.shvmpk.stock_ledger.platform.outbound.PlatformOutboundException;
import com.shvmpk.stock_ledger.platform.outbound.ValidatingDnsResolver;
import com.shvmpk.stock_ledger.platform.security.PlatformCredentialProperties;
import org.apache.hc.client5.http.DnsResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Hardened Outbound Client Unit Tests")
class HardenedOutboundClientTest {

    private static final String KEY = "bf932A02/VQNtKfYxSaasAJGsal1yBYmUMLpZI0jj04=";

    private WireMockServer wireMockServer;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(options().dynamicPort());
        wireMockServer.start();
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    @DisplayName("Should send a request through and return the status, headers and body")
    void shouldReturnResponse() {
        wireMockServer.stubFor(get(urlPathEqualTo("/health"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"ok\"}")));

        OutboundResponse response = clientAllowingLocal().execute(
                OutboundRequest.get(
                        URI.create(wireMockServer.baseUrl() + "/health"),
                        Duration.ofSeconds(2),
                        Map.of("Accept", "application/json")
                )
        );

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{\"status\":\"ok\"}");
        assertThat(response.firstHeader("Content-Type")).contains("application/json");
    }

    @Test
    @DisplayName("Should refuse a hostname that resolves to a private address before connecting")
    void shouldRefuseHostnameResolvingToPrivateAddress() {
        OutboundAddressPolicy policy = strictPolicy();
        DnsResolver privateDns = new DnsResolver() {
            @Override
            public InetAddress[] resolve(String host) throws java.net.UnknownHostException {
                return new InetAddress[]{InetAddress.getByName("10.0.0.5")};
            }

            @Override
            public String resolveCanonicalHostname(String host) {
                return host;
            }
        };
        HardenedOutboundClient client = new HardenedOutboundClient(
                policy,
                new ValidatingDnsResolver(policy, privateDns),
                Duration.ofSeconds(1),
                1024
        );

        assertThatThrownBy(() -> client.execute(
                OutboundRequest.get(
                        URI.create("https://internal.northwind-supply.co/orders"),
                        Duration.ofSeconds(1),
                        Map.of()
                )
        ))
                .isInstanceOf(PlatformOutboundException.class)
                .hasMessageContaining("private or local");
    }

    @Test
    @DisplayName("Should refuse to follow a redirect")
    void shouldRefuseRedirect() {
        wireMockServer.stubFor(get(urlPathEqualTo("/moved"))
                .willReturn(aResponse()
                        .withStatus(302)
                        .withHeader("Location", "https://elsewhere.example.com/orders")));

        assertThatThrownBy(() -> clientAllowingLocal().execute(
                OutboundRequest.get(
                        URI.create(wireMockServer.baseUrl() + "/moved"),
                        Duration.ofSeconds(2),
                        Map.of()
                )
        ))
                .isInstanceOf(PlatformOutboundException.class)
                .hasMessageContaining("redirect");
    }

    @Test
    @DisplayName("Should refuse a response larger than the configured cap")
    void shouldRefuseOversizedResponse() {
        wireMockServer.stubFor(get(urlPathEqualTo("/large"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("x".repeat(4096))));

        HardenedOutboundClient client = clientAllowingLocal(64);

        assertThatThrownBy(() -> client.execute(
                OutboundRequest.get(
                        URI.create(wireMockServer.baseUrl() + "/large"),
                        Duration.ofSeconds(2),
                        Map.of()
                )
        ))
                .isInstanceOf(PlatformOutboundException.class)
                .hasMessageContaining("too large");
    }

    @Test
    @DisplayName("Should apply the request read timeout")
    void shouldTimeOutSlowResponse() {
        wireMockServer.stubFor(get(urlPathEqualTo("/slow"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(1500)
                        .withBody("{\"status\":\"late\"}")));

        assertThatThrownBy(() -> clientAllowingLocal().execute(
                OutboundRequest.get(
                        URI.create(wireMockServer.baseUrl() + "/slow"),
                        Duration.ofMillis(250),
                        Map.of()
                )
        ))
                .isInstanceOf(PlatformOutboundException.class)
                .hasMessageContaining("timed out");
    }

    @Test
    @DisplayName("Should refuse plain HTTP when insecure endpoints are not allowed")
    void shouldRefusePlainHttpInProduction() {
        OutboundAddressPolicy policy = strictPolicy();
        HardenedOutboundClient client = new HardenedOutboundClient(
                policy,
                new ValidatingDnsResolver(policy),
                Duration.ofSeconds(1),
                1024
        );

        assertThatThrownBy(() -> client.execute(
                OutboundRequest.get(
                        URI.create("http://localhost:" + wireMockServer.port() + "/health"),
                        Duration.ofSeconds(1),
                        Map.of()
                )
        ))
                .isInstanceOf(PlatformOutboundException.class)
                .hasMessageContaining("HTTPS");
    }

    private HardenedOutboundClient clientAllowingLocal() {
        return clientAllowingLocal(1024 * 1024);
    }

    private HardenedOutboundClient clientAllowingLocal(int maxResponseBytes) {
        OutboundAddressPolicy policy = new OutboundAddressPolicy(
                new PlatformCredentialProperties(KEY, true)
        );
        return new HardenedOutboundClient(
                policy,
                new ValidatingDnsResolver(policy),
                Duration.ofSeconds(2),
                maxResponseBytes
        );
    }

    private OutboundAddressPolicy strictPolicy() {
        return new OutboundAddressPolicy(new PlatformCredentialProperties(KEY, false));
    }
}
