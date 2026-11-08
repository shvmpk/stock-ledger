package com.shvmpk.stock_ledger.concurrency;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import com.shvmpk.stock_ledger.common.error.InsufficientStockException;
import com.shvmpk.stock_ledger.inventory.entity.InventoryLevel;
import com.shvmpk.stock_ledger.inventory.repository.InventoryRepository;
import com.shvmpk.stock_ledger.inventory.service.InventoryService;
import com.shvmpk.stock_ledger.product.entity.Product;
import com.shvmpk.stock_ledger.product.entity.ProductVariant;
import com.shvmpk.stock_ledger.product.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@DisplayName("Zero-Oversell Atomic Concurrency Test")
class ZeroOversellConcurrencyTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryService inventoryService;

    @Test
    @DisplayName("Concurrent reservations against a single remaining stock item must allow only one reservation")
    void shouldPreventOversellingUnderHighConcurrency() throws InterruptedException {
        // Setup product variant with physical on_hand = 1, reserved = 0 (exactly 1 sellable unit)
        Product product = Product.create(
                UUID.randomUUID(),
                "Limited Edition Merino Knit Sweater",
                "Rare batch handcrafted knitwear",
                "Knitwear"
        );
        ProductVariant variant = product.addVariant(
                UUID.randomUUID(),
                "SWTR-LTD-M",
                "5060123999999",
                "Size",
                "Medium",
                new BigDecimal("65.00"),
                new BigDecimal("180.00"),
                600,
                1
        );
        productRepository.save(product);

        InventoryLevel inventory = InventoryLevel.initialize(UUID.randomUUID(), variant, 1);
        inventoryRepository.save(inventory);

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger insufficientStockCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final UUID orderId = UUID.randomUUID();
            executor.submit(() -> {
                try {
                    startLatch.await(); // Synchronize all threads to fire simultaneously
                    inventoryService.reserveStock(variant.getId(), 1, orderId, null, "Concurrent test checkout");
                    successCount.incrementAndGet();
                } catch (InsufficientStockException ex) {
                    insufficientStockCount.incrementAndGet();
                } catch (Exception ex) {
                    // unexpected error
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Fire all 10 threads at once
        finishLatch.await();
        executor.shutdown();

        // Zero-Oversell Invariant Assertions:
        assertThat(successCount.get())
                .as("Exactly ONE reservation must succeed for the 1 remaining unit")
                .isEqualTo(1);

        assertThat(insufficientStockCount.get())
                .as("The other 9 concurrent attempts must receive InsufficientStockException")
                .isEqualTo(9);

        // Verify database state: on_hand = 1, reserved = 1, available = 0
        InventoryLevel updated = inventoryRepository.findByProductVariantId(variant.getId()).orElseThrow();
        assertThat(updated.getOnHandQuantity()).isEqualTo(1);
        assertThat(updated.getReservedQuantity()).isEqualTo(1);
        assertThat(updated.getAvailableQuantity()).isEqualTo(0);
    }
}
