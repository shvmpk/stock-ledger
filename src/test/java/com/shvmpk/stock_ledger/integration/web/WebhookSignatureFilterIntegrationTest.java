package com.shvmpk.stock_ledger.integration.web;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import com.shvmpk.stock_ledger.config.security.WebhookRequestHasher;
import com.shvmpk.stock_ledger.inventory.entity.InventoryLevel;
import com.shvmpk.stock_ledger.inventory.repository.InventoryRepository;
import com.shvmpk.stock_ledger.inventory.repository.StockMovementRepository;
import com.shvmpk.stock_ledger.order.entity.Order;
import com.shvmpk.stock_ledger.order.entity.OrderSource;
import com.shvmpk.stock_ledger.order.repository.OrderRepository;
import com.shvmpk.stock_ledger.order.repository.OrderReturnRepository;
import com.shvmpk.stock_ledger.platform.dto.UpdatePlatformConnectionRequest;
import com.shvmpk.stock_ledger.platform.dto.WebhookOrderPayload;
import com.shvmpk.stock_ledger.platform.entity.IdempotencyRecord;
import com.shvmpk.stock_ledger.platform.entity.PlatformConnection;
import com.shvmpk.stock_ledger.platform.entity.PlatformType;
import com.shvmpk.stock_ledger.platform.repository.IdempotencyRecordRepository;
import com.shvmpk.stock_ledger.platform.repository.PlatformConnectionRepository;
import com.shvmpk.stock_ledger.platform.security.PlatformCredentialCipher;
import com.shvmpk.stock_ledger.platform.service.PlatformConnectionService;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@DisplayName("Webhook Signature Filter Integration Tests")
class WebhookSignatureFilterIntegrationTest {

    private static final String SHOPIFY_SECRET = "dummy-webhook-secret";
    private static final String WOOCOMMERCE_SECRET = "dummy-webhook-secret-3";
    private static final String ROTATED_SECRET = "whsec_new_7a2c9e4f1b6d8305";
    private static final String SHOPIFY_ACCESS_TOKEN = "dummy-shopify-access-token";
    private static final String WOOCOMMERCE_CONSUMER_KEY = "test-woocommerce-consumer-key";
    private static final String WOOCOMMERCE_CONSUMER_SECRET = "dummy-woocommerce-secret";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private PlatformConnectionRepository platformConnectionRepository;

    @Autowired
    private PlatformConnectionService platformConnectionService;

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
    private PlatformCredentialCipher credentialCipher;

    private MockMvc mockMvc;
    private PlatformConnection shopifyConnection;
    private ProductVariant coffeeVariant;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        cleanDatabase();

        shopifyConnection = PlatformConnection.create(
                UUID.randomUUID(),
                PlatformType.SHOPIFY,
                "https://scandiboutique.myshopify.com/admin/api/2026-10",
                credentialCipher.encrypt(SHOPIFY_SECRET, "webhookSecret"),
                credentialCipher.encrypt(
                        "{\"accessToken\":\"" + SHOPIFY_ACCESS_TOKEN + "\"}",
                        "apiCredentials"
                )
        );
        platformConnectionRepository.saveAndFlush(shopifyConnection);

        Product coffee = Product.create(
                UUID.randomUUID(),
                "Ethiopia Yirgacheffe Washed Coffee",
                "Heirloom varietal, floral jasmine and bergamot notes",
                "Coffee"
        );
        coffeeVariant = coffee.addVariant(
                UUID.randomUUID(),
                "COF-ETH-250G",
                "5060123456790",
                "Grind / Size",
                "Whole Bean / 250g",
                new BigDecimal("6.50"),
                new BigDecimal("14.50"),
                250,
                10
        );
        productRepository.save(coffee);

        inventoryRepository.saveAndFlush(InventoryLevel.initialize(UUID.randomUUID(), coffeeVariant, 10));
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    @DisplayName("Should verify the raw JSON body, reserve stock, and store a SHA-256 request hash")
    void shouldVerifyRawBodyAndStoreRequestHash() throws Exception {
        String rawBody = webhookJson("shp_ord_882910", "ORD-SHP-882910", "Astrid Lindqvist", 2);
        String signature = sign(rawBody, SHOPIFY_SECRET);

        mockMvc.perform(post("/api/v1/webhooks/orders/shopify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Shopify-Hmac-Sha256", signature)
                        .content(rawBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderNumber").value("ORD-SHP-882910"))
                .andExpect(jsonPath("$.data.source").value("WEBHOOK"));

        Order order = orderRepository.findByOrderNumber("ORD-SHP-882910").orElseThrow();
        assertThat(order.getSource()).isEqualTo(OrderSource.WEBHOOK);
        assertThat(order.getExternalOrderId()).isEqualTo("shp_ord_882910");

        InventoryLevel inventory = inventoryRepository.findByProductVariantId(coffeeVariant.getId()).orElseThrow();
        assertThat(inventory.getReservedQuantity()).isEqualTo(2);
        assertThat(inventory.getAvailableQuantity()).isEqualTo(8);

        IdempotencyRecord record = idempotencyRecordRepository
                .findByIdempotencyKey("shopify:shp_ord_882910")
                .orElseThrow();
        assertThat(record.getRequestHash())
                .isEqualTo(WebhookRequestHasher.sha256Hex(rawBody.getBytes(StandardCharsets.UTF_8)));
        assertThat(record.getResponseBody()).isEqualTo(order.getId().toString());

        mockMvc.perform(post("/api/v1/webhooks/orders/shopify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Shopify-Hmac-Sha256", signature)
                        .content(rawBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(order.getId().toString()));

        InventoryLevel afterDuplicate = inventoryRepository
                .findByProductVariantId(coffeeVariant.getId())
                .orElseThrow();
        assertThat(afterDuplicate.getReservedQuantity()).isEqualTo(2);
        assertThat(idempotencyRecordRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should reject a signature computed over payload.toString() instead of raw JSON")
    void shouldRejectSignatureOverPayloadToString() throws Exception {
        String rawBody = webhookJson("shp_ord_bad_shape", "ORD-SHP-BAD-SHAPE", "Astrid Lindqvist", 1);
        WebhookOrderPayload payload = payloadFor(rawBody);
        String signatureOverObject = sign(payload.toString(), SHOPIFY_SECRET);

        mockMvc.perform(post("/api/v1/webhooks/orders/shopify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Shopify-Hmac-Sha256", signatureOverObject)
                        .content(rawBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_WEBHOOK_SIGNATURE"));

        assertThat(orderRepository.count()).isZero();
        assertThat(inventoryRepository.findByProductVariantId(coffeeVariant.getId())
                .orElseThrow()
                .getReservedQuantity()).isZero();
    }

    @Test
    @DisplayName("Should reject a missing signature header")
    void shouldRejectMissingSignatureHeader() throws Exception {
        String rawBody = webhookJson("shp_ord_missing_sig", "ORD-SHP-MISSING-SIG", "Astrid Lindqvist", 1);

        mockMvc.perform(post("/api/v1/webhooks/orders/shopify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_WEBHOOK_SIGNATURE"));
    }

    @Test
    @DisplayName("Should reject when no active connection exists")
    void shouldRejectWhenNoActiveConnectionExists() throws Exception {
        shopifyConnection.pause("Store closed for a seasonal break.");
        platformConnectionRepository.saveAndFlush(shopifyConnection);

        String rawBody = webhookJson("shp_ord_inactive", "ORD-SHP-INACTIVE", "Astrid Lindqvist", 1);
        String signature = sign(rawBody, SHOPIFY_SECRET);

        mockMvc.perform(post("/api/v1/webhooks/orders/shopify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Shopify-Hmac-Sha256", signature)
                        .content(rawBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_WEBHOOK_SIGNATURE"));

        assertThat(orderRepository.count()).isZero();
    }

    @Test
    @DisplayName("Should reject when the stored webhook secret cannot be decrypted")
    void shouldRejectUndecryptableWebhookSecret() throws Exception {
        shopifyConnection.updateWebhookSecret("v1.invalid.invalid");
        platformConnectionRepository.saveAndFlush(shopifyConnection);

        String rawBody = webhookJson("shp_ord_bad_cipher", "ORD-SHP-BAD-CIPHER", "Astrid Lindqvist", 1);
        String signature = sign(rawBody, SHOPIFY_SECRET);

        mockMvc.perform(post("/api/v1/webhooks/orders/shopify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Shopify-Hmac-Sha256", signature)
                        .content(rawBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_WEBHOOK_SIGNATURE"));
    }

    @Test
    @DisplayName("Should accept the generic header and honor Shopify header precedence")
    void shouldHonorHeaderPrecedence() throws Exception {
        String genericBody = webhookJson("shp_ord_generic", "ORD-SHP-GENERIC", "Astrid Lindqvist", 1);
        mockMvc.perform(post("/api/v1/webhooks/orders/shopify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Signature", sign(genericBody, SHOPIFY_SECRET))
                        .content(genericBody))
                .andExpect(status().isOk());

        String conflictingBody = webhookJson("shp_ord_conflict_hdr", "ORD-SHP-CONFLICT-HDR", "Astrid Lindqvist", 1);
        mockMvc.perform(post("/api/v1/webhooks/orders/shopify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Shopify-Hmac-Sha256", "invalid_signature")
                        .header("X-Webhook-Signature", sign(conflictingBody, SHOPIFY_SECRET))
                        .content(conflictingBody))
                .andExpect(status().isUnauthorized());

        assertThat(orderRepository.findByOrderNumber("ORD-SHP-CONFLICT-HDR")).isEmpty();
    }

    @Test
    @DisplayName("Should accept our own universal signature header on any platform path")
    void shouldAcceptUniversalSignatureHeader() throws Exception {
        String rawBody = webhookJson("shp_ord_universal", "ORD-SHP-UNIVERSAL", "Astrid Lindqvist", 1);

        mockMvc.perform(post("/api/v1/webhooks/orders/shopify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-StockLedger-Signature", sign(rawBody, SHOPIFY_SECRET))
                        .content(rawBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNumber").value("ORD-SHP-UNIVERSAL"));
    }

    @Test
    @DisplayName("Should reject a Shopify webhook signed with another vendor's header")
    void shouldRejectForeignVendorSignatureHeader() throws Exception {
        String rawBody = webhookJson("shp_ord_foreign_hdr", "ORD-SHP-FOREIGN-HDR", "Astrid Lindqvist", 1);

        mockMvc.perform(post("/api/v1/webhooks/orders/shopify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-WC-Webhook-Signature", sign(rawBody, SHOPIFY_SECRET))
                        .content(rawBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_WEBHOOK_SIGNATURE"));

        assertThat(orderRepository.findByOrderNumber("ORD-SHP-FOREIGN-HDR")).isEmpty();
    }

    @Test
    @DisplayName("Should accept WooCommerce signatures on the WooCommerce webhook path")
    void shouldAcceptWooCommerceSignature() throws Exception {
        PlatformConnection wooConnection = PlatformConnection.create(
                UUID.randomUUID(),
                PlatformType.WOOCOMMERCE,
                "https://northwind-home.co.uk",
                credentialCipher.encrypt(WOOCOMMERCE_SECRET, "webhookSecret"),
                credentialCipher.encrypt(
                        "{\"consumerKey\":\"" + WOOCOMMERCE_CONSUMER_KEY
                                + "\",\"consumerSecret\":\"" + WOOCOMMERCE_CONSUMER_SECRET + "\"}",
                        "apiCredentials"
                )
        );
        platformConnectionRepository.saveAndFlush(wooConnection);

        String rawBody = webhookJson("wc_order_55102", "ORD-WC-55102", "Freja Lindholm", 1);

        mockMvc.perform(post("/api/v1/webhooks/orders/woocommerce")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-WC-Webhook-Signature", sign(rawBody, WOOCOMMERCE_SECRET))
                        .content(rawBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNumber").value("ORD-WC-55102"));
    }

    @Test
    @DisplayName("Should reject a platform-named webhook path when several stores share that platform")
    void shouldRejectAmbiguousPlatformNameWebhookPath() throws Exception {
        platformConnectionRepository.saveAndFlush(PlatformConnection.create(
                UUID.randomUUID(),
                PlatformType.WOOCOMMERCE,
                "Northwind Home",
                "northwind-home",
                "https://northwind-home.co.uk",
                credentialCipher.encrypt(WOOCOMMERCE_SECRET, "webhookSecret"),
                credentialCipher.encrypt(
                        "{\"consumerKey\":\"" + WOOCOMMERCE_CONSUMER_KEY
                                + "\",\"consumerSecret\":\"" + WOOCOMMERCE_CONSUMER_SECRET + "\"}",
                        "apiCredentials"
                )
        ));
        platformConnectionRepository.saveAndFlush(PlatformConnection.create(
                UUID.randomUUID(),
                PlatformType.WOOCOMMERCE,
                "Harbour & Pine",
                "harbour-pine",
                "https://harbour-and-pine.co.uk",
                credentialCipher.encrypt("dummy-webhook-secret-4", "webhookSecret"),
                credentialCipher.encrypt(
                        "{\"consumerKey\":\"ck_2f8a4c1d9e7b6350a2c5e8f1\""
                                + ",\"consumerSecret\":\"cs_7a1e9d3b6f2c4058a1e9d6b3\"}",
                        "apiCredentials"
                )
        ));

        String rawBody = webhookJson("wc_order_ambiguous", "ORD-WC-AMBIGUOUS", "Freja Lindholm", 1);

        mockMvc.perform(post("/api/v1/webhooks/orders/woocommerce")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-WC-Webhook-Signature", sign(rawBody, WOOCOMMERCE_SECRET))
                        .content(rawBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_WEBHOOK_SIGNATURE"));

        assertThat(orderRepository.findByOrderNumber("ORD-WC-AMBIGUOUS")).isEmpty();
    }

    @Test
    @DisplayName("Should reject a reused idempotency key with a different body hash")
    void shouldRejectReusedIdempotencyKeyWithDifferentHash() throws Exception {
        String firstBody = webhookJson("shp_ord_reuse", "ORD-SHP-REUSE", "Astrid Lindqvist", 1);
        mockMvc.perform(post("/api/v1/webhooks/orders/shopify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Shopify-Hmac-Sha256", sign(firstBody, SHOPIFY_SECRET))
                        .content(firstBody))
                .andExpect(status().isOk());

        String changedBody = webhookJson("shp_ord_reuse", "ORD-SHP-REUSE", "Astrid Lindqvist", 2);
        mockMvc.perform(post("/api/v1/webhooks/orders/shopify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Shopify-Hmac-Sha256", sign(changedBody, SHOPIFY_SECRET))
                        .content(changedBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(inventoryRepository.findByProductVariantId(coffeeVariant.getId())
                .orElseThrow()
                .getReservedQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should reject an incoming order number owned by a different order")
    void shouldRejectOrderNumberConflict() throws Exception {
        orderRepository.saveAndFlush(Order.create(
                "ORD-SHP-CONFLICT-1",
                OrderSource.MANUAL,
                null,
                "Freja Lindholm",
                "freja.lindholm@nordicliving.se",
                "Drottninggatan 12, 411 14 Gothenburg, Sweden",
                new BigDecimal("4.99")
        ));

        String rawBody = webhookJson("shp_ord_conflict_1", "ORD-SHP-CONFLICT-1", "Astrid Lindqvist", 1);

        mockMvc.perform(post("/api/v1/webhooks/orders/shopify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Shopify-Hmac-Sha256", sign(rawBody, SHOPIFY_SECRET))
                        .content(rawBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NUMBER_CONFLICT"));

        assertThat(orderRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should reject the old secret immediately after rotation and accept the new one")
    void shouldRotateWebhookSecret() throws Exception {
        UUID actorId = UUID.randomUUID();
        platformConnectionService.update(
                shopifyConnection.getId(),
                new UpdatePlatformConnectionRequest(
                        shopifyConnection.getVersion(),
                        null, null, ROTATED_SECRET, null, null, null, null
                ),
                actorId
        );

        String oldSecretBody = webhookJson("shp_ord_old_secret", "ORD-SHP-OLD-SECRET", "Astrid Lindqvist", 1);
        mockMvc.perform(post("/api/v1/webhooks/orders/shopify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Shopify-Hmac-Sha256", sign(oldSecretBody, SHOPIFY_SECRET))
                        .content(oldSecretBody))
                .andExpect(status().isUnauthorized());

        String newSecretBody = webhookJson("shp_ord_new_secret", "ORD-SHP-NEW-SECRET", "Astrid Lindqvist", 1);
        mockMvc.perform(post("/api/v1/webhooks/orders/shopify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Shopify-Hmac-Sha256", sign(newSecretBody, ROTATED_SECRET))
                        .content(newSecretBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNumber").value("ORD-SHP-NEW-SECRET"));

        assertThat(orderRepository.findByOrderNumber("ORD-SHP-OLD-SECRET")).isEmpty();
    }

    @Test
    @DisplayName("Should not intercept non-webhook paths")
    void shouldNotInterceptNonWebhookPaths() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
    }

    private String webhookJson(String externalOrderId, String orderNumber, String customerName, int quantity) {
        return """
                {
                  "externalOrderId": "%s",
                  "orderNumber": "%s",
                  "customerName": "%s",
                  "customerEmail": "astrid.lindqvist@scandiboutique.se",
                  "shippingAddress": "Kungsgatan 44, 111 35 Stockholm, Sweden",
                  "shippingCharge": 5.99,
                  "items": [
                    {
                      "sku": "COF-ETH-250G",
                      "quantity": %d,
                      "unitPrice": 14.50
                    }
                  ]
                }
                """.formatted(externalOrderId, orderNumber, customerName, quantity);
    }

    private WebhookOrderPayload payloadFor(String rawBody) {
        return new WebhookOrderPayload(
                "shp_ord_bad_shape",
                "ORD-SHP-BAD-SHAPE",
                "Astrid Lindqvist",
                "astrid.lindqvist@scandiboutique.se",
                "Kungsgatan 44, 111 35 Stockholm, Sweden",
                new BigDecimal("5.99"),
                java.util.List.of(new WebhookOrderPayload.WebhookOrderItem(
                        "COF-ETH-250G",
                        1,
                        new BigDecimal("14.50")
                ))
        );
    }

    private String sign(String body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(
                    mac.doFinal(body.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC computation failed", ex);
        }
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
