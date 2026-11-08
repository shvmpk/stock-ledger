package com.shvmpk.stock_ledger.integration.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import com.shvmpk.stock_ledger.common.error.RetiredProductException;
import com.shvmpk.stock_ledger.inventory.entity.InventoryLevel;
import com.shvmpk.stock_ledger.inventory.repository.InventoryRepository;
import com.shvmpk.stock_ledger.inventory.repository.StockMovementRepository;
import com.shvmpk.stock_ledger.order.entity.Order;
import com.shvmpk.stock_ledger.order.entity.OrderSource;
import com.shvmpk.stock_ledger.order.entity.OrderStatus;
import com.shvmpk.stock_ledger.order.repository.OrderRepository;
import com.shvmpk.stock_ledger.order.repository.OrderReturnRepository;
import com.shvmpk.stock_ledger.platform.dto.PlatformSyncResponse;
import com.shvmpk.stock_ledger.platform.entity.PlatformConnection;
import com.shvmpk.stock_ledger.platform.entity.PlatformCredentials;
import com.shvmpk.stock_ledger.platform.entity.PlatformType;
import com.shvmpk.stock_ledger.platform.repository.IdempotencyRecordRepository;
import com.shvmpk.stock_ledger.platform.repository.PlatformConnectionRepository;
import com.shvmpk.stock_ledger.platform.security.PlatformCredentialCipher;
import com.shvmpk.stock_ledger.platform.security.PlatformCredentialsCodec;
import com.shvmpk.stock_ledger.platform.service.PlatformOrderIntakeService;
import com.shvmpk.stock_ledger.platform.service.PlatformSyncService;
import com.shvmpk.stock_ledger.product.entity.Product;
import com.shvmpk.stock_ledger.product.entity.ProductVariant;
import com.shvmpk.stock_ledger.product.repository.ProductRepository;
import com.shvmpk.stock_ledger.product.repository.ProductVariantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@DisplayName("PlatformSyncService WireMock Integration Tests (Channel 4 Automated Pull Sync)")
class PlatformSyncIntegrationTest {

    private WireMockServer wireMockServer;

    @Autowired
    private PlatformSyncService platformSyncService;

    @Autowired
    private PlatformOrderIntakeService platformOrderIntakeService;

    @Autowired
    private PlatformConnectionRepository platformConnectionRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderReturnRepository orderReturnRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private PlatformCredentialCipher platformCredentialCipher;

    @Autowired
    private PlatformCredentialsCodec platformCredentialsCodec;

    private ProductVariant coffeeVariant;
    private PlatformConnection connection;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(options().dynamicPort());
        wireMockServer.start();

        cleanDatabase();

        // Setup mock connection pointing to WireMock baseUrl
        connection = PlatformConnection.create(
                UUID.randomUUID(),
                PlatformType.SHOPIFY,
                wireMockServer.baseUrl() + "/admin/api/2026-10",
                platformCredentialCipher.encrypt("shpss_secret_sync_test", "webhookSecret"),
                platformCredentialCipher.encrypt(
                        platformCredentialsCodec.encode(new PlatformCredentials(
                                "shpat_token_test_abc123",
                                null,
                                null
                        )),
                        "apiCredentials"
                )
        );
        platformConnectionRepository.save(connection);

        Product coffee = Product.create(
                UUID.randomUUID(),
                "Ethiopia Yirgacheffe Washed Coffee",
                "Heirloom varietal, floral jasmine and bergamot notes",
                "Coffee"
        );
        coffeeVariant = coffee.addVariant(
                UUID.randomUUID(),
                "COF-ETH-SYNC",
                "5060123456791",
                "Grind / Size",
                "Whole Bean / 250g",
                new BigDecimal("6.50"),
                new BigDecimal("14.50"),
                250,
                10
        );
        productRepository.save(coffee);

        InventoryLevel inventory = InventoryLevel.initialize(UUID.randomUUID(), coffeeVariant, 10);
        inventoryRepository.saveAndFlush(inventory);
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    @DisplayName("Should pull orders from external platform via REST, reserve stock atomically, and update sync status")
    void shouldSyncOrdersFromExternalPlatform() {
        String shopifyOrdersJson = """
                {
                  "orders": [
                    {
                      "id": 1099238475,
                      "name": "#1042",
                      "customer": {
                        "first_name": "Astrid",
                        "last_name": "Lindqvist",
                        "email": "astrid@scandiboutique.se"
                      },
                      "shipping_address": {
                        "address1": "Kungsgatan 44",
                        "city": "Stockholm",
                        "zip": "111 35",
                        "country": "Sweden"
                      },
                      "total_shipping_price_set": {
                        "shop_money": { "amount": "4.99" }
                      },
                      "line_items": [
                        {
                          "sku": "COF-ETH-SYNC",
                          "quantity": 2,
                          "price": "14.50"
                        }
                      ]
                    }
                  ]
                }
                """;

        wireMockServer.stubFor(get(urlPathEqualTo("/admin/api/2026-10/orders.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(shopifyOrdersJson)));

        PlatformSyncResponse result = platformSyncService.syncConnection(connection.getId());
        assertThat(result.connectionId()).isEqualTo(connection.getId());
        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.lastSyncAt()).isNotNull();
        assertThat(result.lastErrorMessage()).isNull();

        // Verify order persisted in DB
        Optional<Order> order = orderRepository.findByOrderNumber("ORD-SHP-1042");
        assertThat(order).isPresent();
        assertThat(order.get().getSource()).isEqualTo(OrderSource.PLATFORM_SYNC);
        assertThat(order.get().getCustomerName()).isEqualTo("Astrid Lindqvist");

        // Verify inventory reservation
        InventoryLevel inventory = inventoryRepository.findByProductVariantId(coffeeVariant.getId()).orElseThrow();
        assertThat(inventory.getOnHandQuantity()).isEqualTo(10);
        assertThat(inventory.getReservedQuantity()).isEqualTo(2);
        assertThat(inventory.getAvailableQuantity()).isEqualTo(8);

        // Verify platform connection status updated
        PlatformConnection updated = platformConnectionRepository.findById(connection.getId()).orElseThrow();
        assertThat(updated.getSyncStatus()).isEqualTo("SUCCESS");
        assertThat(updated.getLastSyncAt()).isNotNull();
    }

    @Test
    @DisplayName("Should create a WAITING_FOR_STOCK order when synced order exceeds available stock")
    void shouldCreateWaitingOrderWhenSyncedOrderExceedsStock() {
        String shopifyOrdersJson = """
                {
                  "orders": [
                    {
                      "id": 1099238476,
                      "name": "#1043",
                      "customer": {
                        "first_name": "Lars",
                        "last_name": "Larsson",
                        "email": "lars.larsson@nordicwear.se"
                      },
                      "shipping_address": {
                        "address1": "Stortorget 12",
                        "city": "Malmo",
                        "zip": "211 22",
                        "country": "Sweden"
                      },
                      "total_shipping_price_set": {
                        "shop_money": { "amount": "5.50" }
                      },
                      "line_items": [
                        {
                          "sku": "COF-ETH-SYNC",
                          "quantity": 12,
                          "price": "14.50"
                        }
                      ]
                    }
                  ]
                }
                """;

        wireMockServer.stubFor(get(urlPathEqualTo("/admin/api/2026-10/orders.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(shopifyOrdersJson)));

        platformSyncService.syncConnection(connection.getId());

        Order order = orderRepository.findByOrderNumber("ORD-SHP-1043").orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.WAITING_FOR_STOCK);
        assertThat(order.getItems().getFirst().getReservedQuantity()).isZero();

        InventoryLevel inventory = inventoryRepository.findByProductVariantId(coffeeVariant.getId()).orElseThrow();
        assertThat(inventory.getReservedQuantity()).isZero();
        assertThat(inventory.getAvailableQuantity()).isEqualTo(10);

        PlatformConnection updated = platformConnectionRepository.findById(connection.getId()).orElseThrow();
        assertThat(updated.getSyncStatus()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("Should handle 429 rate limit backoff and record FAILED sync status")
    void shouldHandleRateLimitFromExternalPlatform() {
        wireMockServer.stubFor(get(urlPathEqualTo("/admin/api/2026-10/orders.json"))
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader("Retry-After", "2")
                        .withBody("{\"errors\": \"Exceeded 2 calls per second for api client. Reduce request rates to resume averaging 40 calls per second.\"}")));

        PlatformSyncResponse result = platformSyncService.syncConnection(connection.getId());

        PlatformConnection updated = platformConnectionRepository.findById(connection.getId()).orElseThrow();
        assertThat(updated.getSyncStatus()).isEqualTo("FAILED");
        assertThat(updated.getLastErrorMessage()).contains("429");
        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.lastErrorMessage()).contains("429");

        // Inventory should remain unchanged
        InventoryLevel inventory = inventoryRepository.findByProductVariantId(coffeeVariant.getId()).orElseThrow();
        assertThat(inventory.getReservedQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should commit RUNNING status before HTTP and return the final SUCCESS status")
    void shouldExposeRunningStatusBeforeReturningFinalSuccess() throws Exception {
        wireMockServer.stubFor(get(urlPathEqualTo("/admin/api/2026-10/orders.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"orders\":[]}")
                        .withFixedDelay(500)));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<PlatformSyncResponse> future = executor.submit(
                    () -> platformSyncService.syncConnection(connection.getId())
            );

            String observedStatus = null;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (System.nanoTime() < deadline) {
                observedStatus = platformConnectionRepository.findById(connection.getId())
                        .orElseThrow()
                        .getSyncStatus();
                if ("RUNNING".equals(observedStatus)) {
                    break;
                }
                Thread.sleep(25);
            }

            assertThat(observedStatus).isEqualTo("RUNNING");
            PlatformConnection running = platformConnectionRepository.findById(connection.getId()).orElseThrow();
            assertThat(running.getLastSyncAt()).isNotNull();
            assertThat(running.getLastErrorMessage()).isNull();

            PlatformSyncResponse result = future.get(5, TimeUnit.SECONDS);
            assertThat(result.connectionId()).isEqualTo(connection.getId());
            assertThat(result.status()).isEqualTo("SUCCESS");
            assertThat(result.lastSyncAt()).isNotNull();
            assertThat(result.lastErrorMessage()).isNull();

            PlatformConnection completed = platformConnectionRepository.findById(connection.getId()).orElseThrow();
            assertThat(completed.getSyncStatus()).isEqualTo("SUCCESS");
            assertThat(completed.getLastErrorMessage()).isNull();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("Should mark FAILED and return the final status when the platform request times out")
    void shouldHandleTimeoutFromExternalPlatform() {
        wireMockServer.stubFor(get(urlPathEqualTo("/admin/api/2026-10/orders.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"orders\":[]}")
                        .withFixedDelay(2500)));

        PlatformSyncResponse result = platformSyncService.syncConnection(connection.getId());

        assertThat(result.connectionId()).isEqualTo(connection.getId());
        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.lastErrorMessage()).containsIgnoringCase("timed out");

        PlatformConnection updated = platformConnectionRepository.findById(connection.getId()).orElseThrow();
        assertThat(updated.getSyncStatus()).isEqualTo("FAILED");
        assertThat(updated.getLastErrorMessage()).containsIgnoringCase("timed out");

        InventoryLevel inventory = inventoryRepository.findByProductVariantId(coffeeVariant.getId()).orElseThrow();
        assertThat(inventory.getReservedQuantity()).isZero();
    }

    @Test
    @DisplayName("Should follow Shopify Link pagination and import orders from every page")
    void shouldFollowShopifyLinkPagination() {
        String firstPage = """
                {
                  "orders": [
                    {
                      "id": 1099238490,
                      "name": "#1050",
                      "currency": "SEK",
                      "customer": {
                        "first_name": "Elin",
                        "last_name": "Sundberg",
                        "email": "elin.sundberg@fjallraven.se"
                      },
                      "shipping_address": {
                        "address1": "Vasagatan 7",
                        "city": "Stockholm",
                        "zip": "111 20",
                        "country": "Sweden"
                      },
                      "total_shipping_price_set": {
                        "shop_money": { "amount": "4.99" }
                      },
                      "line_items": [
                        { "sku": "COF-ETH-SYNC", "quantity": 1, "price": "14.50" }
                      ]
                    }
                  ]
                }
                """;
        String secondPage = """
                {
                  "orders": [
                    {
                      "id": 1099238491,
                      "name": "#1051",
                      "currency": "SEK",
                      "customer": {
                        "first_name": "Oskar",
                        "last_name": "Berg",
                        "email": "oskar.berg@fjallraven.se"
                      },
                      "shipping_address": {
                        "address1": "Kungsgatan 18",
                        "city": "Gothenburg",
                        "zip": "411 19",
                        "country": "Sweden"
                      },
                      "total_shipping_price_set": {
                        "shop_money": { "amount": "5.25" }
                      },
                      "line_items": [
                        { "sku": "COF-ETH-SYNC", "quantity": 1, "price": "14.50" }
                      ]
                    }
                  ]
                }
                """;

        wireMockServer.stubFor(get(urlPathEqualTo("/admin/api/2026-10/orders.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader(
                                "Link",
                                "<" + wireMockServer.baseUrl()
                                        + "/admin/api/2026-10/orders-page-2.json?page_info=next>; rel=\"next\""
                        )
                        .withBody(firstPage)));
        wireMockServer.stubFor(get(urlPathEqualTo("/admin/api/2026-10/orders-page-2.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(secondPage)));

        PlatformSyncResponse result = platformSyncService.syncConnection(connection.getId());

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(orderRepository.findByOrderNumber("ORD-SHP-1050")).isPresent();
        assertThat(orderRepository.findByOrderNumber("ORD-SHP-1051")).isPresent();

        InventoryLevel inventory = inventoryRepository.findByProductVariantId(coffeeVariant.getId()).orElseThrow();
        assertThat(inventory.getReservedQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should follow WooCommerce page pagination with Basic auth")
    void shouldFollowWooCommercePagePagination() {
        PlatformConnection wooConnection = createWooCommerceConnection();

        String firstPage = """
                [
                  {
                    "id": 2201,
                    "number": "1044",
                    "currency": "GBP",
                    "shipping_total": "5.25",
                    "billing": {
                      "first_name": "Freja",
                      "last_name": "Lindholm",
                      "email": "freja.lindholm@nordicliving.se"
                    },
                    "shipping": {
                      "address_1": "Drottninggatan 12",
                      "address_2": "",
                      "postcode": "411 14",
                      "city": "Gothenburg",
                      "country": "SE"
                    },
                    "line_items": [
                      { "sku": "COF-ETH-SYNC", "quantity": 1, "price": "14.50" }
                    ]
                  }
                ]
                """;
        String secondPage = """
                [
                  {
                    "id": 2202,
                    "number": "1045",
                    "currency": "GBP",
                    "shipping_total": "4.50",
                    "billing": {
                      "first_name": "Hugo",
                      "last_name": "Ek",
                      "email": "hugo.ek@nordicliving.se"
                    },
                    "shipping": {
                      "address_1": "Sveavagen 33",
                      "address_2": "",
                      "postcode": "111 34",
                      "city": "Stockholm",
                      "country": "SE"
                    },
                    "line_items": [
                      { "sku": "COF-ETH-SYNC", "quantity": 1, "price": "14.50" }
                    ]
                  }
                ]
                """;

        wireMockServer.stubFor(get(urlPathEqualTo("/wp-json/wc/v3/orders"))
                .withQueryParam("page", equalTo("1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("X-WP-TotalPages", "2")
                        .withBody(firstPage)));
        wireMockServer.stubFor(get(urlPathEqualTo("/wp-json/wc/v3/orders"))
                .withQueryParam("page", equalTo("2"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("X-WP-TotalPages", "2")
                        .withBody(secondPage)));

        PlatformSyncResponse result = platformSyncService.syncConnection(wooConnection.getId());

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(orderRepository.findByOrderNumber("ORD-WOO-1044")).isPresent();
        assertThat(orderRepository.findByOrderNumber("ORD-WOO-1045")).isPresent();

        InventoryLevel inventory = inventoryRepository.findByProductVariantId(coffeeVariant.getId()).orElseThrow();
        assertThat(inventory.getReservedQuantity()).isEqualTo(2);

        String expectedAuthorization = "Basic " + Base64.getEncoder().encodeToString(
                ("ck_8f2a6d1c4e9b7350a2f8d6c1:dummy-woocommerce-secret-2")
                        .getBytes(StandardCharsets.UTF_8)
        );
        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/wp-json/wc/v3/orders"))
                .withHeader("Authorization", equalTo(expectedAuthorization)));
    }

    @Test
    @DisplayName("Should mark FAILED with a sanitized message on Shopify authentication failure")
    void shouldMarkFailedOnAuthenticationFailure() {
        wireMockServer.stubFor(get(urlPathEqualTo("/admin/api/2026-10/orders.json"))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"errors\":\"[API] Invalid API key or access token\"}")));

        PlatformSyncResponse result = platformSyncService.syncConnection(connection.getId());

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.lastErrorMessage())
                .contains("Authentication failed (HTTP 401)")
                .doesNotContain("shpat_token_test_abc123");

        PlatformConnection updated = platformConnectionRepository.findById(connection.getId()).orElseThrow();
        assertThat(updated.getSyncStatus()).isEqualTo("FAILED");
        assertThat(updated.getLastErrorMessage()).doesNotContain("shpat_token_test_abc123");
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    @DisplayName("Should mark FAILED with a sanitized message on malformed platform responses")
    void shouldMarkFailedOnMalformedResponse() {
        wireMockServer.stubFor(get(urlPathEqualTo("/admin/api/2026-10/orders.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("this-is-not-json")));

        PlatformSyncResponse result = platformSyncService.syncConnection(connection.getId());

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.lastErrorMessage())
                .contains("Malformed response from SHOPIFY")
                .doesNotContain("shpat_token_test_abc123");

        PlatformConnection updated = platformConnectionRepository.findById(connection.getId()).orElseThrow();
        assertThat(updated.getSyncStatus()).isEqualTo("FAILED");
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    @DisplayName("Should not duplicate orders when the same platform payload is synced twice")
    void shouldNotDuplicateOrdersOnRepeatedSync() {
        String shopifyOrdersJson = """
                {
                  "orders": [
                    {
                      "id": 1099238495,
                      "name": "#1060",
                      "currency": "SEK",
                      "customer": {
                        "first_name": "Maja",
                        "last_name": "Nilsson",
                        "email": "maja.nilsson@nordicwear.se"
                      },
                      "shipping_address": {
                        "address1": "Ostra Hamngatan 5",
                        "city": "Gothenburg",
                        "zip": "411 10",
                        "country": "Sweden"
                      },
                      "total_shipping_price_set": {
                        "shop_money": { "amount": "4.99" }
                      },
                      "line_items": [
                        { "sku": "COF-ETH-SYNC", "quantity": 1, "price": "14.50" }
                      ]
                    }
                  ]
                }
                """;

        wireMockServer.stubFor(get(urlPathEqualTo("/admin/api/2026-10/orders.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(shopifyOrdersJson)));

        PlatformSyncResponse first = platformSyncService.syncConnection(connection.getId());
        PlatformSyncResponse second = platformSyncService.syncConnection(connection.getId());

        assertThat(first.status()).isEqualTo("SUCCESS");
        assertThat(second.status()).isEqualTo("SUCCESS");
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(orderRepository.findByOrderNumber("ORD-SHP-1060")).isPresent();

        InventoryLevel inventory = inventoryRepository.findByProductVariantId(coffeeVariant.getId()).orElseThrow();
        assertThat(inventory.getReservedQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should continue syncing valid orders when one order cannot be imported")
    void shouldContinueWhenOneOrderCannotBeImported() {
        String shopifyOrdersJson = """
                {
                  "orders": [
                    {
                      "id": 1099238496,
                      "name": "#1070",
                      "currency": "SEK",
                      "customer": {
                        "first_name": "Sofia",
                        "last_name": "Lund",
                        "email": "sofia.lund@nordicwear.se"
                      },
                      "shipping_address": {
                        "address1": "Norra Esplanaden 4",
                        "city": "Lund",
                        "zip": "223 50",
                        "country": "Sweden"
                      },
                      "total_shipping_price_set": {
                        "shop_money": { "amount": "4.99" }
                      },
                      "line_items": [
                        { "sku": "COF-ETH-SYNC", "quantity": 1, "price": "14.50" }
                      ]
                    },
                    {
                      "id": 1099238497,
                      "name": "#1071",
                      "currency": "SEK",
                      "customer": {
                        "first_name": "Viktor",
                        "last_name": "Ahl",
                        "email": "viktor.ahl@nordicwear.se"
                      },
                      "shipping_address": {
                        "address1": "Stora Torget 2",
                        "city": "Uppsala",
                        "zip": "753 20",
                        "country": "Sweden"
                      },
                      "total_shipping_price_set": {
                        "shop_money": { "amount": "4.99" }
                      },
                      "line_items": [
                        { "sku": "DISCONTINUED-SKU-999", "quantity": 1, "price": "19.00" }
                      ]
                    }
                  ]
                }
                """;

        wireMockServer.stubFor(get(urlPathEqualTo("/admin/api/2026-10/orders.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(shopifyOrdersJson)));

        PlatformSyncResponse result = platformSyncService.syncConnection(connection.getId());

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(orderRepository.findByOrderNumber("ORD-SHP-1070")).isPresent();
        assertThat(orderRepository.findByOrderNumber("ORD-SHP-1071")).isEmpty();
        assertThat(orderRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should stop WooCommerce pagination at the 20-page cap")
    void shouldCapWooCommercePaginationAtTwentyPages() {
        PlatformConnection wooConnection = createWooCommerceConnection();

        wireMockServer.stubFor(get(urlPathEqualTo("/wp-json/wc/v3/orders"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("X-WP-TotalPages", "25")
                        .withBody("[]")));

        PlatformSyncResponse result = platformSyncService.syncConnection(wooConnection.getId());

        assertThat(result.status()).isEqualTo("SUCCESS");
        wireMockServer.verify(20, getRequestedFor(urlPathEqualTo("/wp-json/wc/v3/orders")));
    }

    @Test
    @DisplayName("Should refuse a platform order whose SKU has been retired")
    void shouldRejectRetiredSku() {
        coffeeVariant.retire();
        productRepository.saveAndFlush(coffeeVariant.getProduct());

        assertThatThrownBy(() -> platformOrderIntakeService.createWaitingOrder(
                OrderSource.PLATFORM_SYNC,
                "ORD-SYNC-RETIRED",
                "shopify-retired-1",
                "Astrid Lindqvist",
                "astrid@scandiboutique.se",
                "Kungsgatan 44, 111 35 Stockholm, Sweden",
                new BigDecimal("4.99"),
                List.of(new PlatformOrderIntakeService.IntakeItem(
                        "COF-ETH-SYNC",
                        1,
                        new BigDecimal("14.50")
                ))
        ))
                .isInstanceOf(RetiredProductException.class)
                .extracting("errorCode")
                .isEqualTo("PRODUCT_RETIRED");

        assertThat(orderRepository.count()).isZero();
    }

    private PlatformConnection createWooCommerceConnection() {
        PlatformConnection wooConnection = PlatformConnection.create(
                UUID.randomUUID(),
                PlatformType.WOOCOMMERCE,
                wireMockServer.baseUrl() + "/wp-json/wc/v3",
                platformCredentialCipher.encrypt("whsec_woo_sync_test", "webhookSecret"),
                platformCredentialCipher.encrypt(
                        platformCredentialsCodec.encode(new PlatformCredentials(
                                null,
                                "ck_8f2a6d1c4e9b7350a2f8d6c1",
                                "dummy-woocommerce-secret-2"
                        )),
                        "apiCredentials"
                )
        );
        return platformConnectionRepository.save(wooConnection);
    }

    private void cleanDatabase() {
        orderReturnRepository.deleteAll();
        orderRepository.deleteAll();
        idempotencyRecordRepository.deleteAll();
        platformConnectionRepository.deleteAll();
        stockMovementRepository.deleteAll();
        inventoryRepository.deleteAll();
        productVariantRepository.deleteAll();
        productRepository.deleteAll();
    }
}
