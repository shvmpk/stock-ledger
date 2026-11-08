package com.shvmpk.stock_ledger.integration.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import com.shvmpk.stock_ledger.platform.dto.PlatformConnectionTestResponse;
import com.shvmpk.stock_ledger.platform.dto.UpdatePlatformConnectionRequest;
import com.shvmpk.stock_ledger.platform.entity.PlatformConnection;
import com.shvmpk.stock_ledger.platform.entity.PlatformConnectionChangeType;
import com.shvmpk.stock_ledger.platform.entity.PlatformConnectionEvent;
import com.shvmpk.stock_ledger.platform.entity.PlatformCredentials;
import com.shvmpk.stock_ledger.platform.entity.PlatformTestOutcome;
import com.shvmpk.stock_ledger.platform.entity.PlatformType;
import com.shvmpk.stock_ledger.platform.repository.PlatformConnectionEventRepository;
import com.shvmpk.stock_ledger.platform.repository.PlatformConnectionRepository;
import com.shvmpk.stock_ledger.platform.security.PlatformCredentialCipher;
import com.shvmpk.stock_ledger.platform.security.PlatformCredentialsCodec;
import com.shvmpk.stock_ledger.platform.service.PlatformConnectionService;
import com.shvmpk.stock_ledger.platform.service.PlatformConnectionTestService;
import com.shvmpk.stock_ledger.support.PlatformConnectionEventTestSupport;
import com.shvmpk.stock_ledger.user.entity.Role;
import com.shvmpk.stock_ledger.user.entity.User;
import com.shvmpk.stock_ledger.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@DisplayName("Platform Connection Test Service Integration Tests")
class PlatformConnectionTestServiceIntegrationTest {

    private static final String SHOPIFY_ACCESS_TOKEN = "dummy-shopify-access-token";
    private static final String WOOCOMMERCE_CONSUMER_KEY = "test-woocommerce-consumer-key";
    private static final String WOOCOMMERCE_CONSUMER_SECRET = "dummy-woocommerce-secret";
    private static final String WEBHOOK_SECRET = "dummy-webhook-secret";

    private WireMockServer wireMockServer;

    @Autowired
    private PlatformConnectionTestService testService;

    @Autowired
    private PlatformConnectionService connectionService;

    @Autowired
    private PlatformConnectionRepository connectionRepository;

    @Autowired
    private PlatformConnectionEventRepository eventRepository;

    @Autowired
    private PlatformCredentialCipher credentialCipher;

    @Autowired
    private PlatformCredentialsCodec credentialsCodec;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User owner;
    private PlatformConnection shopify;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(options().dynamicPort());
        wireMockServer.start();

        PlatformConnectionEventTestSupport.clear(jdbcTemplate);
        connectionRepository.deleteAllInBatch();
        userRepository.findByEmailIgnoreCase("mara.ellison@northwind-supply.co")
                .ifPresent(userRepository::delete);

        owner = userRepository.save(User.create(
                UUID.randomUUID(),
                "mara.ellison@northwind-supply.co",
                passwordEncoder.encode("Northwind-Supply-Owner-2026!"),
                "Mara Ellison",
                Role.OWNER
        ));

        shopify = connectionRepository.saveAndFlush(PlatformConnection.create(
                UUID.randomUUID(),
                PlatformType.SHOPIFY,
                "Northwind Supply",
                "northwind-supply",
                wireMockServer.baseUrl() + "/admin/api/2026-10",
                credentialCipher.encrypt(WEBHOOK_SECRET, "webhookSecret"),
                credentialCipher.encrypt(
                        credentialsCodec.encode(new PlatformCredentials(SHOPIFY_ACCESS_TOKEN, null, null)),
                        "apiCredentials"
                )
        ));
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
        PlatformConnectionEventTestSupport.clear(jdbcTemplate);
        connectionRepository.deleteAllInBatch();
        if (owner != null) {
            userRepository.deleteById(owner.getId());
            owner = null;
        }
    }

    @Test
    @DisplayName("Should report success, keep the row saveable, and record the result in history")
    void shouldReportSuccessAndRecordHistory() {
        wireMockServer.stubFor(get(urlPathEqualTo("/admin/api/2026-10/shop.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"shop\":{\"name\":\"Northwind Supply\"}}")));

        PlatformConnectionTestResponse result = testService.test(shopify.getId(), owner.getId());

        assertThat(result.outcome()).isEqualTo(PlatformTestOutcome.SUCCESS);
        assertThat(result.httpStatus()).isEqualTo(200);
        assertThat(result.message().toLowerCase()).contains("connected");
        assertThat(result.testedAt()).isNotNull();

        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/admin/api/2026-10/shop.json"))
                .withHeader("X-Shopify-Access-Token", equalTo(SHOPIFY_ACCESS_TOKEN)));

        PlatformConnection reloaded = connectionRepository.findById(shopify.getId()).orElseThrow();
        assertThat(reloaded.getLastTestOutcome()).isEqualTo(PlatformTestOutcome.SUCCESS);
        assertThat(reloaded.getLastTestHttpStatus()).isEqualTo(200);
        assertThat(reloaded.getLastTestedAt()).isNotNull();
        assertThat(reloaded.getVersion()).isEqualTo(shopify.getVersion());

        List<PlatformConnectionEvent> events = eventsFor(shopify.getId());
        PlatformConnectionEvent tested = events.stream()
                .filter(event -> event.getChangeType() == PlatformConnectionChangeType.TESTED)
                .findFirst()
                .orElseThrow();
        assertThat(tested.getOutcome()).isEqualTo(PlatformTestOutcome.SUCCESS);
        assertThat(tested.getActorEmail()).isEqualTo("mara.ellison@northwind-supply.co");
        assertThat(tested.getDetail()).doesNotContain(SHOPIFY_ACCESS_TOKEN, WEBHOOK_SECRET);
    }

    @Test
    @DisplayName("Should show a 401 on the connection and still allow the owner to fix the credential")
    void shouldReportAuthenticationFailureAndStillAllowEditing() {
        wireMockServer.stubFor(get(urlPathEqualTo("/admin/api/2026-10/shop.json"))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"errors\":\"[API] Invalid API key or access token\"}")));

        PlatformConnectionTestResponse result = testService.test(shopify.getId(), owner.getId());

        assertThat(result.outcome()).isEqualTo(PlatformTestOutcome.AUTHENTICATION_FAILED);
        assertThat(result.httpStatus()).isEqualTo(401);
        assertThat(result.message()).contains("401");

        PlatformConnection reloaded = connectionRepository.findById(shopify.getId()).orElseThrow();
        assertThat(reloaded.getLastTestOutcome()).isEqualTo(PlatformTestOutcome.AUTHENTICATION_FAILED);
        assertThat(reloaded.getLastTestHttpStatus()).isEqualTo(401);
        assertThat(reloaded.isActive()).isTrue();

        var updated = connectionService.update(
                shopify.getId(),
                new UpdatePlatformConnectionRequest(
                        reloaded.getVersion(),
                        "Northwind Supply EU",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ),
                owner.getId()
        );

        assertThat(updated.displayName()).isEqualTo("Northwind Supply EU");
        assertThat(eventsFor(shopify.getId()))
                .anyMatch(event -> event.getChangeType() == PlatformConnectionChangeType.UPDATED);
    }

    @Test
    @DisplayName("Should report an unreachable result when the channel redirects the test request")
    void shouldReportUnreachableWhenRedirected() {
        wireMockServer.stubFor(get(urlPathEqualTo("/admin/api/2026-10/shop.json"))
                .willReturn(aResponse()
                        .withStatus(302)
                        .withHeader("Location", "https://elsewhere.example.com/shop.json")));

        PlatformConnectionTestResponse result = testService.test(shopify.getId(), owner.getId());

        assertThat(result.outcome()).isEqualTo(PlatformTestOutcome.UNREACHABLE);
        assertThat(result.httpStatus()).isNull();
        assertThat(result.message().toLowerCase()).contains("redirect");

        assertThat(eventsFor(shopify.getId()))
                .filteredOn(event -> event.getChangeType() == PlatformConnectionChangeType.TESTED)
                .singleElement()
                .extracting(PlatformConnectionEvent::getOutcome)
                .isEqualTo(PlatformTestOutcome.UNREACHABLE);
    }

    @Test
    @DisplayName("Should report a WooCommerce success and send Basic authentication")
    void shouldTestWooCommerceWithBasicAuth() {
        PlatformConnection woo = connectionRepository.saveAndFlush(PlatformConnection.create(
                UUID.randomUUID(),
                PlatformType.WOOCOMMERCE,
                "Northwind Home",
                "northwind-home",
                wireMockServer.baseUrl() + "/wp-json/wc/v3",
                credentialCipher.encrypt(WEBHOOK_SECRET, "webhookSecret"),
                credentialCipher.encrypt(
                        credentialsCodec.encode(new PlatformCredentials(
                                null,
                                WOOCOMMERCE_CONSUMER_KEY,
                                WOOCOMMERCE_CONSUMER_SECRET
                        )),
                        "apiCredentials"
                )
        ));
        wireMockServer.stubFor(get(urlPathEqualTo("/wp-json/wc/v3/products"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        PlatformConnectionTestResponse result = testService.test(woo.getId(), owner.getId());

        assertThat(result.outcome()).isEqualTo(PlatformTestOutcome.SUCCESS);
        String expectedAuthorization = "Basic " + Base64.getEncoder().encodeToString(
                (WOOCOMMERCE_CONSUMER_KEY + ":" + WOOCOMMERCE_CONSUMER_SECRET)
                        .getBytes(StandardCharsets.UTF_8)
        );
        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/wp-json/wc/v3/products"))
                .withQueryParam("per_page", equalTo("1"))
                .withHeader("Authorization", equalTo(expectedAuthorization)));
    }

    @Test
    @DisplayName("Should report awaiting first inbound order for a webhook-only channel and never call out")
    void shouldReportAwaitingFirstInboundOrder() {
        PlatformConnection custom = connectionRepository.saveAndFlush(PlatformConnection.create(
                UUID.randomUUID(),
                PlatformType.CUSTOM_WEBHOOK,
                "Studio Craft Fair",
                "studio-craft-fair",
                "https://your-automation.example.com/stockledger/orders",
                credentialCipher.encrypt(WEBHOOK_SECRET, "webhookSecret"),
                null
        ));

        PlatformConnectionTestResponse first = testService.test(custom.getId(), owner.getId());

        assertThat(first.outcome()).isEqualTo(PlatformTestOutcome.AWAITING_INBOUND);
        assertThat(first.httpStatus()).isNull();
        assertThat(first.lastInboundOrderAt()).isNull();
        assertThat(first.message()).contains("first inbound order");
        assertThat(eventsFor(custom.getId())).isEmpty();

        OffsetDateTime receivedAt = OffsetDateTime.parse("2026-10-24T08:15:00Z");
        jdbcTemplate.update(
                "UPDATE platform_connections SET last_inbound_order_at = ? WHERE id = ?",
                receivedAt,
                custom.getId()
        );

        PlatformConnectionTestResponse second = testService.test(custom.getId(), owner.getId());

        assertThat(second.outcome()).isEqualTo(PlatformTestOutcome.AWAITING_INBOUND);
        assertThat(second.lastInboundOrderAt()).isEqualTo(receivedAt);
        assertThat(second.message()).contains("Last order received");
        assertThat(wireMockServer.getAllServeEvents()).isEmpty();
    }

    private List<PlatformConnectionEvent> eventsFor(UUID connectionId) {
        return eventRepository.findByPlatformConnectionId(
                connectionId,
                PageRequest.of(0, 20)
        ).getContent();
    }
}
