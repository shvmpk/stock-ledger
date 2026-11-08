package com.shvmpk.stock_ledger.integration.service;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import com.shvmpk.stock_ledger.common.error.IdempotencyKeyReusedException;
import com.shvmpk.stock_ledger.common.error.OrderNumberConflictException;
import com.shvmpk.stock_ledger.common.error.RetiredProductException;
import com.shvmpk.stock_ledger.config.security.WebhookRequestHasher;
import com.shvmpk.stock_ledger.inventory.entity.InventoryLevel;
import com.shvmpk.stock_ledger.inventory.repository.InventoryRepository;
import com.shvmpk.stock_ledger.inventory.repository.StockMovementRepository;
import com.shvmpk.stock_ledger.order.dto.OrderResponse;
import com.shvmpk.stock_ledger.order.entity.Order;
import com.shvmpk.stock_ledger.order.entity.OrderSource;
import com.shvmpk.stock_ledger.order.entity.OrderStatus;
import com.shvmpk.stock_ledger.order.repository.OrderRepository;
import com.shvmpk.stock_ledger.order.repository.OrderReturnRepository;
import com.shvmpk.stock_ledger.platform.dto.WebhookOrderPayload;
import com.shvmpk.stock_ledger.platform.entity.IdempotencyRecord;
import com.shvmpk.stock_ledger.platform.entity.PlatformConnection;
import com.shvmpk.stock_ledger.platform.entity.PlatformType;
import com.shvmpk.stock_ledger.platform.repository.IdempotencyRecordRepository;
import com.shvmpk.stock_ledger.platform.repository.PlatformConnectionRepository;
import com.shvmpk.stock_ledger.platform.service.WebhookOrderService;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@DisplayName("WebhookOrderService Integration Tests (Idempotency & Stock Reservation)")
class WebhookOrderIntegrationTest {

    @Autowired
    private WebhookOrderService webhookOrderService;

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

    private ProductVariant coffeeVariant;

    @BeforeEach
    void setUp() {
        cleanDatabase();

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
    @DisplayName("Should ingest a webhook order, reserve stock, and replay the same payload idempotently")
    void shouldIngestWebhookOrderSuccessfully() {
        WebhookOrderPayload payload = payload("shp_ord_882910", "ORD-SHP-882910", 2);
        String requestHash = requestHash(payload);

        OrderResponse response = webhookOrderService.processWebhookOrder("shopify", payload, requestHash);

        assertThat(response).isNotNull();
        assertThat(response.orderNumber()).isEqualTo("ORD-SHP-882910");
        assertThat(response.source()).isEqualTo("WEBHOOK");

        Optional<Order> order = orderRepository.findByOrderNumber("ORD-SHP-882910");
        assertThat(order).isPresent();
        assertThat(order.get().getSource()).isEqualTo(OrderSource.WEBHOOK);
        assertThat(order.get().getExternalOrderId()).isEqualTo("shp_ord_882910");

        InventoryLevel inventory = inventoryRepository.findByProductVariantId(coffeeVariant.getId()).orElseThrow();
        assertThat(inventory.getOnHandQuantity()).isEqualTo(10);
        assertThat(inventory.getReservedQuantity()).isEqualTo(2);
        assertThat(inventory.getAvailableQuantity()).isEqualTo(8);

        IdempotencyRecord record = idempotencyRecordRepository
                .findByIdempotencyKey("shopify:shp_ord_882910")
                .orElseThrow();
        assertThat(record.getRequestHash()).isEqualTo(requestHash);
        assertThat(record.getResponseBody()).isEqualTo(response.id().toString());

        OrderResponse duplicateResponse = webhookOrderService.processWebhookOrder(
                "shopify",
                payload,
                requestHash
        );
        assertThat(duplicateResponse.id()).isEqualTo(response.id());

        InventoryLevel inventoryAfterDuplicate = inventoryRepository
                .findByProductVariantId(coffeeVariant.getId())
                .orElseThrow();
        assertThat(inventoryAfterDuplicate.getReservedQuantity()).isEqualTo(2);
        assertThat(idempotencyRecordRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should reject a reused idempotency key when the request hash changes")
    void shouldRejectReusedIdempotencyKeyWithDifferentHash() {
        WebhookOrderPayload firstPayload = payload("shp_ord_reused", "ORD-SHP-REUSED", 1);
        webhookOrderService.processWebhookOrder("shopify", firstPayload, requestHash(firstPayload));

        WebhookOrderPayload changedPayload = payload("shp_ord_reused", "ORD-SHP-REUSED", 2);

        assertThatThrownBy(() -> webhookOrderService.processWebhookOrder(
                "shopify",
                changedPayload,
                requestHash(changedPayload)
        ))
                .isInstanceOf(IdempotencyKeyReusedException.class)
                .extracting("errorCode")
                .isEqualTo("IDEMPOTENCY_KEY_REUSED");

        InventoryLevel inventory = inventoryRepository.findByProductVariantId(coffeeVariant.getId()).orElseThrow();
        assertThat(inventory.getReservedQuantity()).isEqualTo(1);
        assertThat(orderRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should reject an incoming order number that already belongs to another order")
    void shouldRejectOrderNumberConflict() {
        orderRepository.saveAndFlush(Order.create(
                "ORD-SHP-2026-0100",
                OrderSource.MANUAL,
                null,
                "Freja Lindholm",
                "freja.lindholm@nordicliving.se",
                "Drottninggatan 12, 411 14 Gothenburg, Sweden",
                new BigDecimal("4.99")
        ));

        WebhookOrderPayload payload = payload("shp_ord_conflict", "ORD-SHP-2026-0100", 1);

        assertThatThrownBy(() -> webhookOrderService.processWebhookOrder(
                "shopify",
                payload,
                requestHash(payload)
        ))
                .isInstanceOf(OrderNumberConflictException.class)
                .extracting("errorCode")
                .isEqualTo("ORDER_NUMBER_CONFLICT");

        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(idempotencyRecordRepository.count()).isZero();
    }

    @Test
    @DisplayName("Should create a WAITING_FOR_STOCK order when webhook stock is insufficient")
    void shouldCreateWaitingOrderWhenStockIsInsufficient() {
        WebhookOrderPayload payload = payload("shp_ord_backlog", "ORD-SHP-BACKLOG", 12);
        String requestHash = requestHash(payload);

        OrderResponse response = webhookOrderService.processWebhookOrder("shopify", payload, requestHash);

        assertThat(response.status()).isEqualTo(OrderStatus.WAITING_FOR_STOCK);
        Order order = orderRepository.findByOrderNumber("ORD-SHP-BACKLOG").orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.WAITING_FOR_STOCK);
        assertThat(order.getItems().getFirst().getReservedQuantity()).isZero();

        InventoryLevel inventory = inventoryRepository.findByProductVariantId(coffeeVariant.getId()).orElseThrow();
        assertThat(inventory.getReservedQuantity()).isZero();
        assertThat(inventory.getAvailableQuantity()).isEqualTo(10);
        assertThat(idempotencyRecordRepository.findByIdempotencyKey("shopify:shp_ord_backlog"))
                .isPresent();

        OrderResponse duplicate = webhookOrderService.processWebhookOrder("shopify", payload, requestHash);
        assertThat(duplicate.id()).isEqualTo(response.id());
        assertThat(duplicate.status()).isEqualTo(OrderStatus.WAITING_FOR_STOCK);
    }

    @Test
    @DisplayName("Should record when an inbound order was last received, and not move it for a replay")
    void shouldRecordLastInboundOrderAt() {
        PlatformConnection connection = platformConnectionRepository.saveAndFlush(
                PlatformConnection.create(
                        UUID.randomUUID(),
                        PlatformType.SHOPIFY,
                        "Shopify",
                        "shopify",
                        "https://northwind-supply.myshopify.com",
                        "v1.placeholder",
                        null
                )
        );
        WebhookOrderPayload payload = payload("shp_ord_inbound_clock", "ORD-SHP-INBOUND-CLOCK", 1);
        String requestHash = requestHash(payload);

        webhookOrderService.processWebhookOrder("shopify", payload, requestHash, connection.getId());

        PlatformConnection afterFirst = platformConnectionRepository.findById(connection.getId()).orElseThrow();
        assertThat(afterFirst.getLastInboundOrderAt()).isNotNull();
        OffsetDateTime firstReceivedAt = afterFirst.getLastInboundOrderAt();

        webhookOrderService.processWebhookOrder("shopify", payload, requestHash, connection.getId());

        PlatformConnection afterReplay = platformConnectionRepository.findById(connection.getId()).orElseThrow();
        assertThat(afterReplay.getLastInboundOrderAt()).isEqualTo(firstReceivedAt);
    }

    @Test
    @DisplayName("Should reject a webhook order whose SKU has been retired")
    void shouldRejectRetiredSku() {
        coffeeVariant.retire();
        productRepository.saveAndFlush(coffeeVariant.getProduct());

        WebhookOrderPayload payload = payload("shp_ord_retired", "ORD-SHP-RETIRED", 1);

        assertThatThrownBy(() -> webhookOrderService.processWebhookOrder(
                "shopify",
                payload,
                requestHash(payload)
        ))
                .isInstanceOf(RetiredProductException.class)
                .extracting("errorCode")
                .isEqualTo("PRODUCT_RETIRED");

        assertThat(orderRepository.count()).isZero();
        assertThat(idempotencyRecordRepository.count()).isZero();

        InventoryLevel inventory = inventoryRepository.findByProductVariantId(coffeeVariant.getId()).orElseThrow();
        assertThat(inventory.getReservedQuantity()).isZero();
    }

    private WebhookOrderPayload payload(String externalOrderId, String orderNumber, int quantity) {
        return new WebhookOrderPayload(
                externalOrderId,
                orderNumber,
                "Elin Sundqvist",
                "elin.sundqvist@goteborg.se",
                "Avenyn 5, 411 36 Gothenburg, Sweden",
                new BigDecimal("3.50"),
                List.of(new WebhookOrderPayload.WebhookOrderItem(
                        "COF-ETH-250G",
                        quantity,
                        new BigDecimal("14.50")
                ))
        );
    }

    private String requestHash(WebhookOrderPayload payload) {
        return WebhookRequestHasher.sha256Hex(
                payload.toString().getBytes(StandardCharsets.UTF_8)
        );
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
