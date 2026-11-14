package com.shvmpk.stock_ledger.concurrency;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import com.shvmpk.stock_ledger.inventory.entity.InventoryLevel;
import com.shvmpk.stock_ledger.inventory.repository.InventoryRepository;
import com.shvmpk.stock_ledger.order.dto.CreateOrderRequest;
import com.shvmpk.stock_ledger.order.dto.CreateOrderItemRequest;
import com.shvmpk.stock_ledger.order.dto.OrderResponse;
import com.shvmpk.stock_ledger.order.service.OrderService;
import com.shvmpk.stock_ledger.product.entity.Product;
import com.shvmpk.stock_ledger.product.entity.ProductVariant;
import com.shvmpk.stock_ledger.product.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the order-number collision bug surfaced by the k6
 * steady load profile (docs/pages/LOAD-TESTING.md, finding 1): order
 * numbers drawn randomly from a finite space collide once the stored
 * order history grows, and the colliding insert fails with a raw 500.
 *
 * Fires 1,500 concurrent manual orders and demands that every one of
 * them succeeds with a distinct order number — which a random generator
 * cannot guarantee (expected ≈12 collisions at this volume) but a
 * database sequence hands out atomically.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@DisplayName("Order-Number Uniqueness Concurrency Test")
class OrderNumberUniquenessConcurrencyTest {

    private static final int THREADS = 30;
    private static final int ORDERS_PER_THREAD = 50;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private OrderService orderService;

    @Test
    @DisplayName("1,500 concurrent orders must all succeed with distinct order numbers")
    void shouldNeverCollideOnOrderNumbersUnderConcurrency() throws InterruptedException {
        Product product = Product.create(
                UUID.randomUUID(),
                "A5 Dot-Grid Hardcover Notebook",
                "Thread-bound 192-page notebook, 100 gsm ivory paper",
                "Stationery"
        );
        ProductVariant variant = product.addVariant(
                UUID.randomUUID(),
                "NTBK-DOT-A5",
                "5060123444444",
                "Format",
                "A5 Dotted",
                new BigDecimal("6.80"),
                new BigDecimal("14.00"),
                350,
                20
        );
        productRepository.save(product);

        // Enough on-hand stock that every order's reservation succeeds;
        // this test isolates number generation, not the oversell guard.
        InventoryLevel inventory = InventoryLevel.initialize(UUID.randomUUID(), variant, 2000);
        inventoryRepository.save(inventory);

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(THREADS);

        Set<String> orderNumbers = ConcurrentHashMap.newKeySet();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicReference<String> firstFailure = new AtomicReference<>();

        for (int t = 0; t < THREADS; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < ORDERS_PER_THREAD; i++) {
                        try {
                            OrderResponse created = orderService.createManualOrder(
                                    orderRequest(ThreadLocalRandom.current().nextInt(CUSTOMERS.length)));
                            orderNumbers.add(created.orderNumber());
                            successCount.incrementAndGet();
                        } catch (Exception ex) {
                            failureCount.incrementAndGet();
                            firstFailure.compareAndSet(null, ex.getClass().getSimpleName() + ": " + ex.getMessage());
                        }
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        finishLatch.await();
        executor.shutdown();

        int expectedOrders = THREADS * ORDERS_PER_THREAD;

        assertThat(failureCount.get())
                .as("No concurrent checkout may fail on a duplicate order number; first failure: %s", firstFailure.get())
                .isZero();

        assertThat(successCount.get())
                .as("All %d concurrent orders must be created", expectedOrders)
                .isEqualTo(expectedOrders);

        assertThat(orderNumbers)
                .as("Order numbers handed out concurrently must all be distinct")
                .hasSize(expectedOrders);
    }

    private static final String[][] CUSTOMERS = {
            {"Marguerite Delacroix", "marguerite.delacroix@postevolution.fr", "9 Impasse des Lilas, Lyon, 69003, France"},
            {"Callum Ferguson", "callum.ferguson@celticmail.uk", "42 Bristo Place, Edinburgh, EH1 1EZ, United Kingdom"},
            {"Sanne van der Berg", "sanne.vdberg@postbus.nl", "Keizersgracht 118, Amsterdam, 1015 CW, Netherlands"},
            {"Leila Haddad", "leila.haddad@mashreqpost.com", "Rue Gouraud 27, Beirut, 2011 3310, Lebanon"},
    };

    private CreateOrderRequest orderRequest(int customerIndex) {
        String[] customer = CUSTOMERS[customerIndex];
        return new CreateOrderRequest(
                customer[0],
                customer[1],
                customer[2],
                new BigDecimal("4.99"),
                List.of(new CreateOrderItemRequest("NTBK-DOT-A5", 1, new BigDecimal("14.00")))
        );
    }
}
