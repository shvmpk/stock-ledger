package com.shvmpk.stock_ledger.integration.service;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import com.shvmpk.stock_ledger.common.error.InsufficientReservedStockException;
import com.shvmpk.stock_ledger.common.error.InvalidOrderStateTransitionException;
import com.shvmpk.stock_ledger.common.error.ShippingPriceNotConfiguredException;
import com.shvmpk.stock_ledger.inventory.entity.InventoryLevel;
import com.shvmpk.stock_ledger.inventory.entity.StockMovement;
import com.shvmpk.stock_ledger.inventory.entity.StockMovementReason;
import com.shvmpk.stock_ledger.inventory.repository.InventoryRepository;
import com.shvmpk.stock_ledger.inventory.repository.StockMovementRepository;
import com.shvmpk.stock_ledger.order.dto.CreateOrderItemRequest;
import com.shvmpk.stock_ledger.order.dto.CreateOrderRequest;
import com.shvmpk.stock_ledger.order.dto.OrderResponse;
import com.shvmpk.stock_ledger.order.dto.ShipOrderRequest;
import com.shvmpk.stock_ledger.order.entity.Order;
import com.shvmpk.stock_ledger.order.entity.OrderItem;
import com.shvmpk.stock_ledger.order.entity.OrderSource;
import com.shvmpk.stock_ledger.order.entity.OrderStatus;
import com.shvmpk.stock_ledger.order.repository.OrderRepository;
import com.shvmpk.stock_ledger.order.service.OrderService;
import com.shvmpk.stock_ledger.product.entity.Product;
import com.shvmpk.stock_ledger.product.entity.ProductVariant;
import com.shvmpk.stock_ledger.product.repository.ProductRepository;
import com.shvmpk.stock_ledger.shipping.entity.Carrier;
import com.shvmpk.stock_ledger.shipping.entity.ParcelSize;
import com.shvmpk.stock_ledger.shipping.entity.ShippingPriceRate;
import com.shvmpk.stock_ledger.shipping.repository.ShippingPriceListRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
@DisplayName("Order Lifecycle State Machine Integration Tests (Pack, Ship, Cancel)")
class OrderLifecycleIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private ShippingPriceListRepository shippingPriceListRepository;

    @Autowired
    private com.shvmpk.stock_ledger.order.repository.OrderReturnRepository orderReturnRepository;

    @Autowired
    private com.shvmpk.stock_ledger.product.repository.ProductVariantRepository productVariantRepository;

    private ProductVariant hoodieVariant;

    @BeforeEach
    void setUp() {
        orderReturnRepository.deleteAll();
        orderRepository.deleteAll();
        stockMovementRepository.deleteAll();
        inventoryRepository.deleteAll();
        productVariantRepository.deleteAll();
        productRepository.deleteAll();
        shippingPriceListRepository.deleteAll();

        Product hoodie = Product.create(
                UUID.randomUUID(),
                "Heavyweight Cotton Hoodie",
                "450 GSM organic French terry cotton hoodie",
                "Apparel"
        );
        hoodieVariant = hoodie.addVariant(
                UUID.randomUUID(),
                "HOOD-NVY-LC",
                "5060123456789",
                "Color / Size",
                "Navy / XL",
                new BigDecimal("28.00"),
                new BigDecimal("68.00"),
                750,
                5
        );
        productRepository.save(hoodie);

        InventoryLevel inventory = InventoryLevel.initialize(UUID.randomUUID(), hoodieVariant, 10);
        inventoryRepository.save(inventory);

        ShippingPriceRate rate = ShippingPriceRate.create(
                UUID.randomUUID(),
                Carrier.DHL,
                ParcelSize.MEDIUM,
                new BigDecimal("6.50")
        );
        shippingPriceListRepository.save(rate);
    }

    @Test
    @DisplayName("Should transition order PENDING -> READY_TO_SHIP -> SHIPPED with atomic stock deduction and tracking URL")
    void shouldTransitionOrderLifecycleToShipped() {
        CreateOrderRequest request = new CreateOrderRequest(
                "Astrid Lindqvist",
                "astrid@scandiboutique.se",
                "Kungsgatan 44, 111 35 Stockholm, Sweden",
                new BigDecimal("4.99"),
                List.of(new CreateOrderItemRequest("HOOD-NVY-LC", 2, new BigDecimal("68.00")))
        );
        OrderResponse placedOrder = orderService.createManualOrder(request);

        // 1. Pack order
        OrderResponse packedOrder = orderService.packOrder(placedOrder.id());
        assertThat(packedOrder.status()).isEqualTo(OrderStatus.READY_TO_SHIP);

        // 2. Ship order
        ShipOrderRequest shipRequest = new ShipOrderRequest(
                Carrier.DHL,
                ParcelSize.MEDIUM,
                "1234567890"
        );
        OrderResponse shippedOrder = orderService.shipOrder(placedOrder.id(), shipRequest);

        assertThat(shippedOrder.status()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(shippedOrder.carrier()).isEqualTo("DHL");
        assertThat(shippedOrder.parcelSize()).isEqualTo("MEDIUM");
        assertThat(shippedOrder.trackingNumber()).isEqualTo("1234567890");
        assertThat(shippedOrder.trackingUrl()).isEqualTo("https://www.dhl.com/en/express/tracking.html?AWB=1234567890");
        assertThat(shippedOrder.actualShippingCost()).isEqualByComparingTo("6.50");

        // Verify inventory deduction (on-hand 10 -> 8, reserved 2 -> 0, available remains 8)
        InventoryLevel inventory = inventoryRepository.findByProductVariantId(hoodieVariant.getId()).orElseThrow();
        assertThat(inventory.getOnHandQuantity()).isEqualTo(8);
        assertThat(inventory.getReservedQuantity()).isEqualTo(0);
        assertThat(inventory.getAvailableQuantity()).isEqualTo(8);

        // Verify audit movement for ORDER_SHIPPED
        List<StockMovement> movements = stockMovementRepository.findAll();
        assertThat(movements).anyMatch(m ->
                m.getReason() == StockMovementReason.ORDER_SHIPPED
                        && m.getDeltaOnHand() == -2
                        && m.getDeltaReserved() == -2
        );
    }

    @Test
    @DisplayName("Should reject invalid state transition when shipping already shipped order")
    void shouldRejectInvalidStateTransition() {
        CreateOrderRequest request = new CreateOrderRequest(
                "Astrid Lindqvist",
                "astrid@scandiboutique.se",
                "Kungsgatan 44, 111 35 Stockholm, Sweden",
                new BigDecimal("4.99"),
                List.of(new CreateOrderItemRequest("HOOD-NVY-LC", 2, new BigDecimal("68.00")))
        );
        OrderResponse placedOrder = orderService.createManualOrder(request);
        orderService.packOrder(placedOrder.id());

        ShipOrderRequest shipRequest = new ShipOrderRequest(Carrier.DHL, ParcelSize.MEDIUM, "1234567890");
        orderService.shipOrder(placedOrder.id(), shipRequest);

        // Re-shipping should fail
        assertThatThrownBy(() -> orderService.shipOrder(placedOrder.id(), shipRequest))
                .isInstanceOf(InvalidOrderStateTransitionException.class)
                .hasMessageContaining("Cannot transition order from status 'SHIPPED' to 'SHIPPED'");
    }

    @Test
    @DisplayName("Should reject shipment when the reserved stock was released")
    void shouldRejectShipmentWhenReservedStockIsInsufficient() {
        CreateOrderRequest request = new CreateOrderRequest(
                "Astrid Lindqvist",
                "astrid@scandiboutique.se",
                "Kungsgatan 44, 111 35 Stockholm, Sweden",
                new BigDecimal("4.99"),
                List.of(new CreateOrderItemRequest("HOOD-NVY-LC", 2, new BigDecimal("68.00")))
        );
        OrderResponse placedOrder = orderService.createManualOrder(request);
        orderService.packOrder(placedOrder.id());

        inventoryRepository.atomicReleaseStock(hoodieVariant.getId(), 2);

        ShipOrderRequest shipRequest = new ShipOrderRequest(Carrier.DHL, ParcelSize.MEDIUM, "1234567890");

        assertThatThrownBy(() -> orderService.shipOrder(placedOrder.id(), shipRequest))
                .isInstanceOf(InsufficientReservedStockException.class)
                .hasMessageContaining(
                        "Cannot ship 2 units of SKU 'HOOD-NVY-LC'. Only 0 are reserved for open orders, "
                                + "while 10 are on the shelf. Check the order's reservation or record a stock count."
                )
                .extracting(ex -> ((InsufficientReservedStockException) ex).getErrorCode())
                .isEqualTo("INSUFFICIENT_RESERVED_STOCK");
    }

    @Test
    @DisplayName("Should cancel pending order and release reserved stock back to available pool")
    void shouldCancelPendingOrderAndReleaseStock() {
        CreateOrderRequest request = new CreateOrderRequest(
                "Astrid Lindqvist",
                "astrid@scandiboutique.se",
                "Kungsgatan 44, 111 35 Stockholm, Sweden",
                new BigDecimal("4.99"),
                List.of(new CreateOrderItemRequest("HOOD-NVY-LC", 3, new BigDecimal("68.00")))
        );
        OrderResponse placedOrder = orderService.createManualOrder(request);

        // Cancel order
        OrderResponse cancelledOrder = orderService.cancelOrder(placedOrder.id(), "Customer changed mind");
        assertThat(cancelledOrder.status()).isEqualTo(OrderStatus.CANCELLED);

        // Stock should be released
        InventoryLevel inventory = inventoryRepository.findByProductVariantId(hoodieVariant.getId()).orElseThrow();
        assertThat(inventory.getOnHandQuantity()).isEqualTo(10);
        assertThat(inventory.getReservedQuantity()).isEqualTo(0);
        assertThat(inventory.getAvailableQuantity()).isEqualTo(10);

        List<StockMovement> movements = stockMovementRepository.findAll();
        assertThat(movements).anyMatch(m ->
                m.getReason() == StockMovementReason.ORDER_CANCELLED
                        && m.getDeltaReserved() == -3
        );
    }

    @Test
    @DisplayName("Should cancel a waiting order without writing a reservation release movement")
    void shouldCancelWaitingOrderWithoutReleasingReservedStock() {
        Order waiting = Order.createWaiting(
                "ORD-CSV-7701",
                OrderSource.CSV,
                "csv-row-7701",
                "Freja Lindholm",
                "freja.lindholm@nordicliving.se",
                "Drottninggatan 12, 411 14 Gothenburg, Sweden",
                new BigDecimal("4.99")
        );
        waiting.addItem(OrderItem.createUnreserved(
                waiting,
                hoodieVariant,
                2,
                new BigDecimal("68.00")
        ));
        orderRepository.saveAndFlush(waiting);

        OrderResponse cancelled = orderService.cancelOrder(
                waiting.getId(),
                "Customer cancelled while waiting for restock"
        );

        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(inventoryRepository.findByProductVariantId(hoodieVariant.getId()).orElseThrow()
                .getReservedQuantity()).isZero();
        assertThat(stockMovementRepository.findAll())
                .noneMatch(movement -> movement.getReason() == StockMovementReason.ORDER_CANCELLED);
        assertThat(orderRepository.findById(waiting.getId()).orElseThrow()
                .getItems().getFirst().getReservedQuantity()).isZero();
    }

    @Test
    @DisplayName("Should record the actual shipping cost override instead of the active rate")
    void shouldRecordActualShippingCostOverride() {
        OrderResponse placed = placeOrder(2);
        orderService.packOrder(placed.id());

        OrderResponse shipped = orderService.shipOrder(
                placed.id(),
                new ShipOrderRequest(
                        Carrier.DHL,
                        ParcelSize.MEDIUM,
                        "1234567890",
                        new BigDecimal("4.35")
                )
        );

        assertThat(shipped.actualShippingCost()).isEqualByComparingTo("4.35");
        assertThat(orderRepository.findById(placed.id()).orElseThrow().getActualShippingCost())
                .isEqualByComparingTo("4.35");
        assertThat(inventoryRepository.findByProductVariantId(hoodieVariant.getId()).orElseThrow()
                .getOnHandQuantity()).isEqualTo(8);
        assertThat(stockMovementRepository.findAll())
                .anyMatch(movement -> movement.getReason() == StockMovementReason.ORDER_SHIPPED
                        && movement.getDeltaOnHand() == -2
                        && movement.getDeltaReserved() == -2);
    }

    @Test
    @DisplayName("Should return SHIPPING_PRICE_NOT_CONFIGURED when no active rate exists and no override is supplied")
    void shouldRejectShippingWhenRateIsMissing() {
        OrderResponse placed = placeOrder(1);
        orderService.packOrder(placed.id());

        assertThatThrownBy(() -> orderService.shipOrder(
                placed.id(),
                new ShipOrderRequest(Carrier.UPS, ParcelSize.LARGE, "1Z999AA10123456784", null)
        ))
                .isInstanceOf(ShippingPriceNotConfiguredException.class)
                .hasMessageContaining("UPS")
                .hasMessageContaining("LARGE")
                .extracting(ex -> ((ShippingPriceNotConfiguredException) ex).getErrorCode())
                .isEqualTo("SHIPPING_PRICE_NOT_CONFIGURED");

        Order unchanged = orderRepository.findById(placed.id()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(OrderStatus.READY_TO_SHIP);
        assertThat(unchanged.getActualShippingCost()).isNull();
        InventoryLevel inventory = inventoryRepository.findByProductVariantId(hoodieVariant.getId()).orElseThrow();
        assertThat(inventory.getOnHandQuantity()).isEqualTo(10);
        assertThat(inventory.getReservedQuantity()).isEqualTo(1);
        assertThat(stockMovementRepository.findAll())
                .noneMatch(movement -> movement.getReason() == StockMovementReason.ORDER_SHIPPED);
    }

    @Test
    @DisplayName("Should deduct the item reserved quantity, not the ordered quantity, when shipping")
    void shouldShipOnlyTheReservedQuantity() {
        OrderResponse placed = placeOrder(3);
        orderService.packOrder(placed.id());

        Order order = orderRepository.findById(placed.id()).orElseThrow();
        OrderItem item = order.getItems().getFirst();
        inventoryRepository.atomicReleaseStock(hoodieVariant.getId(), 1);
        item.updateReservedQuantity(2);
        orderRepository.saveAndFlush(order);

        OrderResponse shipped = orderService.shipOrder(
                placed.id(),
                new ShipOrderRequest(Carrier.DHL, ParcelSize.MEDIUM, "1234567890", null)
        );

        assertThat(shipped.status()).isEqualTo(OrderStatus.SHIPPED);
        InventoryLevel inventory = inventoryRepository.findByProductVariantId(hoodieVariant.getId()).orElseThrow();
        assertThat(inventory.getOnHandQuantity()).isEqualTo(8);
        assertThat(inventory.getReservedQuantity()).isZero();
        assertThat(stockMovementRepository.findAll())
                .anyMatch(movement -> movement.getReason() == StockMovementReason.ORDER_SHIPPED
                        && movement.getDeltaOnHand() == -2
                        && movement.getDeltaReserved() == -2);
    }

    @Test
    @DisplayName("Should reject shipment when an order item has zero reserved quantity")
    void shouldRejectShipmentWhenItemReservedQuantityIsZero() {
        OrderResponse placed = placeOrder(2);
        orderService.packOrder(placed.id());

        Order order = orderRepository.findById(placed.id()).orElseThrow();
        OrderItem item = order.getItems().getFirst();
        inventoryRepository.atomicReleaseStock(hoodieVariant.getId(), 2);
        item.updateReservedQuantity(0);
        orderRepository.saveAndFlush(order);

        assertThatThrownBy(() -> orderService.shipOrder(
                placed.id(),
                new ShipOrderRequest(Carrier.DHL, ParcelSize.MEDIUM, "1234567890", null)
        ))
                .isInstanceOf(InsufficientReservedStockException.class)
                .hasMessageContaining("HOOD-NVY-LC")
                .extracting(ex -> ((InsufficientReservedStockException) ex).getErrorCode())
                .isEqualTo("INSUFFICIENT_RESERVED_STOCK");

        Order unchanged = orderRepository.findById(placed.id()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(OrderStatus.READY_TO_SHIP);
        assertThat(unchanged.getItems().getFirst().getReservedQuantity()).isZero();
        InventoryLevel inventory = inventoryRepository.findByProductVariantId(hoodieVariant.getId()).orElseThrow();
        assertThat(inventory.getOnHandQuantity()).isEqualTo(10);
        assertThat(inventory.getReservedQuantity()).isZero();
        assertThat(stockMovementRepository.findAll())
                .noneMatch(movement -> movement.getReason() == StockMovementReason.ORDER_SHIPPED);
    }

    private OrderResponse placeOrder(int quantity) {
        CreateOrderRequest request = new CreateOrderRequest(
                "Astrid Lindqvist",
                "astrid@scandiboutique.se",
                "Kungsgatan 44, 111 35 Stockholm, Sweden",
                new BigDecimal("4.99"),
                List.of(new CreateOrderItemRequest("HOOD-NVY-LC", quantity, new BigDecimal("68.00")))
        );
        return orderService.createManualOrder(request);
    }
}
