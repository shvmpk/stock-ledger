package com.shvmpk.stock_ledger.concurrency;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import com.shvmpk.stock_ledger.common.error.InsufficientStockException;
import com.shvmpk.stock_ledger.inventory.entity.InventoryLevel;
import com.shvmpk.stock_ledger.inventory.entity.StockMovementReason;
import com.shvmpk.stock_ledger.inventory.repository.InventoryRepository;
import com.shvmpk.stock_ledger.inventory.repository.StockMovementRepository;
import com.shvmpk.stock_ledger.order.dto.CreateOrderItemRequest;
import com.shvmpk.stock_ledger.order.dto.CreateOrderRequest;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@DisplayName("Allocation Race Concurrency Tests (FIFO Locking & Last-Unit Safety)")
class AllocationRaceConcurrencyTest {

    @Autowired
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
                "COF-ETH-RACE",
                "5060123456792",
                "Grind / Size",
                "Whole Bean / 250g",
                new BigDecimal("6.50"),
                new BigDecimal("14.50"),
                250,
                10
        );
        productRepository.saveAndFlush(coffee);
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    @DisplayName("Should allocate a waiting order only once when two workers race for it")
    void shouldAllocateWaitingOrderOnlyOnceUnderConcurrency() throws Exception {
        inventoryRepository.saveAndFlush(InventoryLevel.initialize(UUID.randomUUID(), coffeeVariant, 1));
        createWaitingOrder("ORD-RACE-5101");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<List<String>> allocation = () -> {
            ready.countDown();
            start.await(10, TimeUnit.SECONDS);
            return backlogAllocationService.allocateWaitingForVariant(coffeeVariant.getId(), null);
        };

        try {
            Future<List<String>> first = executor.submit(allocation);
            Future<List<String>> second = executor.submit(allocation);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<String> firstResult = first.get(20, TimeUnit.SECONDS);
            List<String> secondResult = second.get(20, TimeUnit.SECONDS);

            long allocatedOccurrences = List.of(firstResult, secondResult).stream()
                    .flatMap(List::stream)
                    .filter("ORD-RACE-5101"::equals)
                    .count();
            assertThat(allocatedOccurrences).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }

        Order order = orderRepository.findByOrderNumber("ORD-RACE-5101").orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getItems().getFirst().getReservedQuantity()).isEqualTo(1);

        InventoryLevel inventory = inventoryRepository.findByProductVariantId(coffeeVariant.getId()).orElseThrow();
        assertThat(inventory.getReservedQuantity()).isEqualTo(1);
        assertThat(inventory.getAvailableQuantity()).isZero();
        assertThat(stockMovementRepository.findAll().stream()
                .filter(movement -> movement.getReason() == StockMovementReason.ORDER_RESERVED)
                .count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should let exactly one winner claim the last unit when manual intake races with allocation")
    void shouldAllowExactlyOneWinnerForLastUnit() throws Exception {
        inventoryRepository.saveAndFlush(InventoryLevel.initialize(UUID.randomUUID(), coffeeVariant, 1));
        createWaitingOrder("ORD-RACE-5201");

        CreateOrderRequest manualRequest = new CreateOrderRequest(
                "Astrid Lindqvist",
                "astrid.lindqvist@scandiboutique.se",
                "Kungsgatan 44, 111 35 Stockholm, Sweden",
                new BigDecimal("5.99"),
                List.of(new CreateOrderItemRequest("COF-ETH-RACE", 1, new BigDecimal("14.50")))
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<Boolean> manual = () -> {
            ready.countDown();
            start.await(10, TimeUnit.SECONDS);
            try {
                orderService.createManualOrder(manualRequest);
                return true;
            } catch (InsufficientStockException ex) {
                return false;
            }
        };
        Callable<Boolean> allocation = () -> {
            ready.countDown();
            start.await(10, TimeUnit.SECONDS);
            return !backlogAllocationService
                    .allocateWaitingForVariant(coffeeVariant.getId(), null)
                    .isEmpty();
        };

        try {
            Future<Boolean> manualResult = executor.submit(manual);
            Future<Boolean> allocationResult = executor.submit(allocation);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            boolean manualWon = manualResult.get(20, TimeUnit.SECONDS);
            boolean allocationWon = allocationResult.get(20, TimeUnit.SECONDS);
            assertThat(List.of(manualWon, allocationWon).stream().filter(Boolean::booleanValue).count())
                    .isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }

        InventoryLevel inventory = inventoryRepository.findByProductVariantId(coffeeVariant.getId()).orElseThrow();
        assertThat(inventory.getOnHandQuantity()).isEqualTo(1);
        assertThat(inventory.getReservedQuantity()).isEqualTo(1);
        assertThat(inventory.getAvailableQuantity()).isZero();

        long pendingOrders = orderRepository.findAll().stream()
                .filter(order -> order.getStatus() == OrderStatus.PENDING)
                .count();
        assertThat(pendingOrders).isEqualTo(1);
        assertThat(stockMovementRepository.findAll().stream()
                .filter(movement -> movement.getReason() == StockMovementReason.ORDER_RESERVED)
                .count()).isEqualTo(1);
    }

    private void createWaitingOrder(String orderNumber) {
        Order order = Order.createWaiting(
                orderNumber,
                OrderSource.CSV,
                "csv-row-" + orderNumber,
                "Freja Lindholm",
                "freja.lindholm@nordicliving.se",
                "Drottninggatan 12, 411 14 Gothenburg, Sweden",
                new BigDecimal("4.99")
        );
        order.addItem(OrderItem.createUnreserved(
                order,
                coffeeVariant,
                1,
                new BigDecimal("14.50")
        ));
        orderRepository.saveAndFlush(order);
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
