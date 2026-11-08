package com.shvmpk.stock_ledger.integration.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import com.shvmpk.stock_ledger.platform.dto.PlatformConnectionTestResponse;
import com.shvmpk.stock_ledger.platform.dto.PlatformSyncResponse;
import com.shvmpk.stock_ledger.platform.entity.PlatformConnection;
import com.shvmpk.stock_ledger.platform.entity.PlatformCredentials;
import com.shvmpk.stock_ledger.platform.entity.PlatformTestOutcome;
import com.shvmpk.stock_ledger.platform.entity.PlatformType;
import com.shvmpk.stock_ledger.platform.repository.PlatformConnectionRepository;
import com.shvmpk.stock_ledger.platform.security.PlatformCredentialCipher;
import com.shvmpk.stock_ledger.platform.security.PlatformCredentialsCodec;
import com.shvmpk.stock_ledger.platform.service.PlatformConnectionTestService;
import com.shvmpk.stock_ledger.platform.service.PlatformSyncService;
import com.shvmpk.stock_ledger.support.PlatformConnectionEventTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves that the production policy — not the test escape hatch — is what
 * guards the outbound path. The address here is a hostname (localhost) that
 * resolves to a loopback address, which the hardened client must refuse before
 * any connection is opened.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "app.platform.allow-insecure-endpoints=false")
@DisplayName("Platform Outbound Hardening Integration Tests")
class PlatformOutboundHardeningIntegrationTest {

    private static final String SHOPIFY_ACCESS_TOKEN = "dummy-shopify-access-token";
    private static final String WEBHOOK_SECRET = "dummy-webhook-secret";

    private WireMockServer wireMockServer;

    @Autowired
    private PlatformConnectionTestService testService;

    @Autowired
    private PlatformSyncService syncService;

    @Autowired
    private PlatformConnectionRepository connectionRepository;

    @Autowired
    private PlatformCredentialCipher credentialCipher;

    @Autowired
    private PlatformCredentialsCodec credentialsCodec;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private PlatformConnection connection;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(options().dynamicPort());
        wireMockServer.start();

        PlatformConnectionEventTestSupport.clear(jdbcTemplate);
        connectionRepository.deleteAllInBatch();

        connection = connectionRepository.saveAndFlush(PlatformConnection.create(
                UUID.randomUUID(),
                PlatformType.SHOPIFY,
                "Northwind Supply",
                "northwind-supply",
                "https://localhost:" + wireMockServer.port() + "/admin/api/2026-10",
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
    }

    @Test
    @DisplayName("Should refuse a hostname resolving to loopback when testing a connection")
    void shouldRefuseLoopbackHostnameWhenTesting() {
        PlatformConnectionTestResponse result = testService.test(connection.getId(), null);

        assertThat(result.outcome()).isEqualTo(PlatformTestOutcome.UNREACHABLE);
        assertThat(result.message()).contains("private or local");
        assertThat(wireMockServer.getAllServeEvents()).isEmpty();
    }

    @Test
    @DisplayName("Should refuse a hostname resolving to loopback during order sync")
    void shouldRefuseLoopbackHostnameWhenSyncing() {
        PlatformSyncResponse result = syncService.syncConnection(connection.getId());

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.lastErrorMessage()).contains("private or local");
        assertThat(wireMockServer.getAllServeEvents()).isEmpty();
    }
}
