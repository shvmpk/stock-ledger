package com.shvmpk.stock_ledger.integration.service;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import com.shvmpk.stock_ledger.inventory.dto.ReceiveStockResult;
import com.shvmpk.stock_ledger.inventory.entity.InventoryLevel;
import com.shvmpk.stock_ledger.inventory.entity.StockMovement;
import com.shvmpk.stock_ledger.inventory.entity.StockMovementReason;
import com.shvmpk.stock_ledger.inventory.repository.InventoryRepository;
import com.shvmpk.stock_ledger.inventory.repository.StockMovementRepository;
import com.shvmpk.stock_ledger.inventory.service.StockReceivingService;
import com.shvmpk.stock_ledger.order.entity.Order;
import com.shvmpk.stock_ledger.order.entity.OrderItem;
import com.shvmpk.stock_ledger.order.entity.OrderSource;
import com.shvmpk.stock_ledger.order.entity.OrderStatus;
import com.shvmpk.stock_ledger.order.repository.OrderRepository;
import com.shvmpk.stock_ledger.order.repository.OrderReturnRepository;
import com.shvmpk.stock_ledger.order.service.BacklogAllocationService;
import com.shvmpk.stock_ledger.order.service.OrderService;
import com.shvmpk.stock_ledger.platform.repository.IdempotencyRecordRepository;
import com.shvmpk.stock_ledger.platform.repository.PlatformConnectionRepository;
import com.shvmpk.stock_ledger.product.entity.Product;
import com.shvmpk.stock_ledger.product.entity.ProductVariant;
import com.shvmpk.stock_ledger.product.repository.ProductRepository;
import com.shvmpk.stock_ledger.product.repository.ProductVariantRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@DisplayName("Backlog Allocation & Receiving Integration Tests (FIFO Waiting Orders)")
class BacklogAllocationIntegrationTest {

    @Autowired
    private StockReceivingService stockReceivingService;

    @MockitoSpyBean
    private BacklogAllocationService backlogAllocationService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderReturnRepository orderReturnRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    private PlatformConnectionRepository platformConnectionRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private UserRepository userRepository;

    private ProductVariant coffeeVariant;
    private ProductVariant grinderVariant;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        cleanDatabase();

        User warehouseOperator = User.create(
                UUID.randomUUID(),
                "freja.lindholm@nordicliving.se",
                "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy",
                "Freja Lindholm",
                Role.STAFF
        );
        actorId = userRepository.saveAndFlush(warehouseOperator).getId();

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
        productRepository.saveAndFlush(coffee);

        Product grinder = Product.create(
                UUID.randomUUID(),
                "Comandante C40 Hand Grinder",
                "High-precision stainless steel burr coffee grinder",
                "Equipment"
        );
        grinderVariant = grinder.addVariant(
                UUID.randomUUID(),
                "GRD-C40-WHT",
                "5060123456806",
                "Color",
                "White",
                new BigDecimal("135.00"),
                new BigDecimal("295.00"),
                620,
                3
        );
        productRepository.saveAndFlush(grinder);

        inventoryRepository.saveAndFlush(InventoryLevel.initialize(UUID.randomUUID(), coffeeVariant, 0));
        inventoryRepository.saveAndFlush(InventoryLevel.initialize(UUID.randomUUID(), grinderVariant, 0));
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    @DisplayName("Should keep a waiting order waiting when the receipt is still insufficient")
    void shouldKeepOrderWaitingWhenReceiptIsInsufficient() {
        createWaitingOrder(
                "ORD-WAIT-4101",
                List.of(new WaitingLine(coffeeVariant, 2)),
                OrderSource.CSV,
                "csv-row-4101"
        );

        ReceiveStockResult result = stockReceivingService.receive(
                coffeeVariant.getId(),
                1,
                actorId,
                "Partial supplier delivery from Nordic Coffee Collective"
        );

        assertThat(result.stockLevel().onHandQuantity()).isEqualTo(1);
        assertThat(result.stockLevel().reservedQuantity()).isZero();
        assertThat(result.stockLevel().availableQuantity()).isEqualTo(1);
        assertThat(result.allocatedOrderNumbers()).isEmpty();

        Order order = orderRepository.findByOrderNumber("ORD-WAIT-4101").orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.WAITING_FOR_STOCK);
        assertThat(order.getItems().getFirst().getReservedQuantity()).isZero();

        assertThat(stockMovementRepository.findAll())
                .anyMatch(movement -> movement.getReason() == StockMovementReason.RECEIVE)
                .noneMatch(movement -> movement.getReason() == StockMovementReason.ORDER_RESERVED);
    }

    @Test
    @DisplayName("Should allocate the oldest fully fulfillable waiting order first")
    void shouldAllocateOldestWaitingOrderFirst() {
        Order first = createWaitingOrder(
                "ORD-WAIT-4101",
                List.of(new WaitingLine(coffeeVariant, 1)),
                OrderSource.CSV,
                "csv-row-4101"
        );
        createWaitingOrder(
                "ORD-WAIT-4102",
                List.of(new WaitingLine(coffeeVariant, 1)),
                OrderSource.CSV,
                "csv-row-4102"
        );

        ReceiveStockResult result = stockReceivingService.receive(
                coffeeVariant.getId(),
                1,
                actorId,
                "Emergency restock from Nordic Coffee Collective"
        );

        assertThat(result.allocatedOrderNumbers()).containsExactly("ORD-WAIT-4101");

        Order allocated = orderRepository.findByOrderNumber("ORD-WAIT-4101").orElseThrow();
        Order stillWaiting = orderRepository.findByOrderNumber("ORD-WAIT-4102").orElseThrow();
        assertThat(allocated.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(allocated.getItems().getFirst().getReservedQuantity()).isEqualTo(1);
        assertThat(stillWaiting.getStatus()).isEqualTo(OrderStatus.WAITING_FOR_STOCK);

        InventoryLevel inventory = inventoryRepository.findByProductVariantId(coffeeVariant.getId()).orElseThrow();
        assertThat(inventory.getOnHandQuantity()).isEqualTo(1);
        assertThat(inventory.getReservedQuantity()).isEqualTo(1);
        assertThat(inventory.getAvailableQuantity()).isZero();

        List<StockMovement> movements = stockMovementRepository.findAll();
        assertThat(movements).anyMatch(movement ->
                movement.getReason() == StockMovementReason.ORDER_RESERVED
                        && movement.getReferenceId().equals(first.getId())
                        && movement.getDeltaReserved() == 1
        );
    }

    @Test
    @DisplayName("Should allocate a multi-item waiting order only when every item is fulfillable")
    void shouldAllocateMultiItemOrderAllOrNothing() {
        createWaitingOrder(
                "ORD-WAIT-4201",
                List.of(
                        new WaitingLine(coffeeVariant, 1),
                        new WaitingLine(grinderVariant, 1)
                ),
                OrderSource.WEBHOOK,
                "shp_ord_4201"
        );

        ReceiveStockResult coffeeReceipt = stockReceivingService.receive(
                coffeeVariant.getId(),
                1,
                actorId,
                "Coffee restock while grinder remains out of stock"
        );

        assertThat(coffeeReceipt.allocatedOrderNumbers()).isEmpty();
        assertThat(orderRepository.findByOrderNumber("ORD-WAIT-4201").orElseThrow().getStatus())
                .isEqualTo(OrderStatus.WAITING_FOR_STOCK);
        assertThat(inventoryRepository.findByProductVariantId(coffeeVariant.getId()).orElseThrow())
                .satisfies(inventory -> {
                    assertThat(inventory.getOnHandQuantity()).isEqualTo(1);
                    assertThat(inventory.getReservedQuantity()).isZero();
                });
        assertThat(inventoryRepository.findByProductVariantId(grinderVariant.getId()).orElseThrow())
                .satisfies(inventory -> {
                    assertThat(inventory.getOnHandQuantity()).isZero();
                    assertThat(inventory.getReservedQuantity()).isZero();
                });

        ReceiveStockResult grinderReceipt = stockReceivingService.receive(
                grinderVariant.getId(),
                1,
                actorId,
                "Grinder restock completes the waiting order"
        );

        assertThat(grinderReceipt.allocatedOrderNumbers()).containsExactly("ORD-WAIT-4201");
        Order allocated = orderRepository.findByOrderNumber("ORD-WAIT-4201").orElseThrow();
        assertThat(allocated.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(allocated.getItems())
                .allSatisfy(item -> assertThat(item.getReservedQuantity()).isEqualTo(1));

        assertThat(inventoryRepository.findByProductVariantId(coffeeVariant.getId()).orElseThrow()
                .getReservedQuantity()).isEqualTo(1);
        assertThat(inventoryRepository.findByProductVariantId(grinderVariant.getId()).orElseThrow()
                .getReservedQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should skip a cancelled waiting order during allocation")
    void shouldSkipCancelledWaitingOrder() {
        Order waiting = createWaitingOrder(
                "ORD-WAIT-4301",
                List.of(new WaitingLine(coffeeVariant, 1)),
                OrderSource.CSV,
                "csv-row-4301"
        );

        orderService.cancelOrder(waiting.getId(), "Customer cancelled before restock", actorId);

        ReceiveStockResult result = stockReceivingService.receive(
                coffeeVariant.getId(),
                1,
                actorId,
                "Restock after cancellation"
        );

        assertThat(result.allocatedOrderNumbers()).isEmpty();
        Order cancelled = orderRepository.findByOrderNumber("ORD-WAIT-4301").orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.getItems().getFirst().getReservedQuantity()).isZero();
        assertThat(inventoryRepository.findByProductVariantId(coffeeVariant.getId()).orElseThrow()
                .getReservedQuantity()).isZero();
        assertThat(stockMovementRepository.findAll())
                .noneMatch(movement -> movement.getReason() == StockMovementReason.ORDER_RESERVED);
    }

    @Test
    @DisplayName("Should commit a receipt even when backlog allocation throws unexpectedly")
    void shouldCommitReceiptWhenAllocationThrowsUnexpectedly() {
        createWaitingOrder(
                "ORD-WAIT-4401",
                List.of(new WaitingLine(coffeeVariant, 1)),
                OrderSource.CSV,
                "csv-row-4401"
        );

        doThrow(new IllegalStateException("Allocation index is temporarily unavailable"))
                .when(backlogAllocationService)
                .allocateWaitingForVariant(any(), any());

        ReceiveStockResult result = stockReceivingService.receive(
                coffeeVariant.getId(),
                1,
                actorId,
                "Supplier delivery during allocation outage"
        );

        assertThat(result.stockLevel().onHandQuantity()).isEqualTo(1);
        assertThat(result.stockLevel().reservedQuantity()).isZero();
        assertThat(result.allocatedOrderNumbers()).isEmpty();

        Order order = orderRepository.findByOrderNumber("ORD-WAIT-4401").orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.WAITING_FOR_STOCK);
        assertThat(stockMovementRepository.findAll())
                .anyMatch(movement -> movement.getReason() == StockMovementReason.RECEIVE)
                .noneMatch(movement -> movement.getReason() == StockMovementReason.ORDER_RESERVED);

        reset(backlogAllocationService);
        List<String> retried = backlogAllocationService.allocateAllWaiting(actorId);
        assertThat(retried).containsExactly("ORD-WAIT-4401");
        assertThat(orderRepository.findByOrderNumber("ORD-WAIT-4401").orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING);
        assertThat(inventoryRepository.findByProductVariantId(coffeeVariant.getId()).orElseThrow()
                .getReservedQuantity()).isEqualTo(1);
    }

    private Order createWaitingOrder(
            String orderNumber,
            List<WaitingLine> lines,
            OrderSource source,
            String externalId
    ) {
        Order order = Order.createWaiting(
                orderNumber,
                source,
                externalId,
                "Freja Lindholm",
                "freja.lindholm@nordicliving.se",
                "Drottninggatan 12, 411 14 Gothenburg, Sweden",
                new BigDecimal("4.99")
        );
        for (WaitingLine line : lines) {
            order.addItem(OrderItem.createUnreserved(
                    order,
                    line.variant(),
                    line.quantity(),
                    line.variant().getSellingPrice()
            ));
        }
        return orderRepository.saveAndFlush(order);
    }

    private void cleanDatabase() {
        orderReturnRepository.deleteAll();
        orderRepository.deleteAll();
        idempotencyRecordRepository.deleteAll();
        platformConnectionRepository.deleteAll();
        stockMovementRepository.deleteAll();
        userRepository.deleteAll();
        inventoryRepository.deleteAll();
        productVariantRepository.deleteAll();
        productRepository.deleteAll();
    }

    private record WaitingLine(ProductVariant variant, int quantity) {
    }
}
