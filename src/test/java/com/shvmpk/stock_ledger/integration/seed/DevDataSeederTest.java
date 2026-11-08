package com.shvmpk.stock_ledger.integration.seed;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import com.shvmpk.stock_ledger.config.seed.DevDataSeeder;
import com.shvmpk.stock_ledger.inventory.entity.InventoryLevel;
import com.shvmpk.stock_ledger.inventory.entity.StockMovement;
import com.shvmpk.stock_ledger.inventory.entity.StockMovementReason;
import com.shvmpk.stock_ledger.inventory.repository.InventoryRepository;
import com.shvmpk.stock_ledger.inventory.repository.StockCountItemRepository;
import com.shvmpk.stock_ledger.inventory.repository.StockCountRepository;
import com.shvmpk.stock_ledger.inventory.repository.StockMovementRepository;
import com.shvmpk.stock_ledger.order.entity.Order;
import com.shvmpk.stock_ledger.order.entity.OrderReturn;
import com.shvmpk.stock_ledger.order.entity.OrderStatus;
import com.shvmpk.stock_ledger.order.repository.OrderRepository;
import com.shvmpk.stock_ledger.order.repository.OrderReturnRepository;
import com.shvmpk.stock_ledger.platform.entity.PlatformConnection;
import com.shvmpk.stock_ledger.platform.entity.PlatformType;
import com.shvmpk.stock_ledger.platform.repository.PlatformConnectionRepository;
import com.shvmpk.stock_ledger.product.entity.ProductVariant;
import com.shvmpk.stock_ledger.product.repository.ProductRepository;
import com.shvmpk.stock_ledger.product.repository.ProductVariantRepository;
import com.shvmpk.stock_ledger.shipping.repository.ShippingPriceListRepository;
import com.shvmpk.stock_ledger.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@DisplayName("DevDataSeeder Integration Test (BA.13)")
class DevDataSeederTest {

    @Autowired
    private DevDataSeeder devDataSeeder;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private StockCountRepository stockCountRepository;

    @Autowired
    private StockCountItemRepository stockCountItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderReturnRepository orderReturnRepository;

    @Autowired
    private PlatformConnectionRepository platformConnectionRepository;

    @Autowired
    private ShippingPriceListRepository shippingPriceListRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        cleanDatabase();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    @DisplayName("Should seed at least 30 products, 80+ variants, inventory, and every order status")
    void shouldSeedCompleteHighFidelityDataset() {
        devDataSeeder.seed();

        long productCount = productRepository.count();
        long variantCount = productVariantRepository.count();
        assertThat(productCount).isGreaterThanOrEqualTo(30);
        assertThat(variantCount).isGreaterThanOrEqualTo(80);

        long inventoryCount = inventoryRepository.count();
        assertThat(inventoryCount).isGreaterThanOrEqualTo(80);

        long movementCount = stockMovementRepository.count();
        assertThat(movementCount).isGreaterThanOrEqualTo(80);

        long totalOrders = orderRepository.count();
        assertThat(totalOrders).isGreaterThanOrEqualTo(10);

        assertThat(orderRepository.findAll())
                .extracting(Order::getStatus)
                .contains(OrderStatus.values());

        long rateCount = shippingPriceListRepository.count();
        assertThat(rateCount).isGreaterThanOrEqualTo(6);

        assertThat(userRepository.existsByEmailIgnoreCase("owner@stockledger.io")).isTrue();
        assertThat(userRepository.existsByEmailIgnoreCase("staff@stockledger.io")).isTrue();

        List<PlatformConnection> platforms = platformConnectionRepository.findAll();
        assertThat(platforms).hasSize(2);

        PlatformConnection shopify = platforms.stream()
                .filter(connection -> connection.getPlatformType() == PlatformType.SHOPIFY)
                .findFirst()
                .orElseThrow();
        assertThat(shopify.isActive()).isTrue();
        assertThat(shopify.getSyncStatus()).isEqualTo("SUCCESS");
        assertThat(shopify.getLastSyncAt()).isBefore(OffsetDateTime.now().minusHours(1));
        assertThat(shopify.getLastErrorMessage()).isNull();
        assertThat(shopify.getWebhookSecretEncrypted()).startsWith("v1.");
        assertThat(shopify.getEncryptedApiCredentials()).startsWith("v1.");

        PlatformConnection wooCommerce = platforms.stream()
                .filter(connection -> connection.getPlatformType() == PlatformType.WOOCOMMERCE)
                .findFirst()
                .orElseThrow();
        assertThat(wooCommerce.isActive()).isTrue();
        assertThat(wooCommerce.getSyncStatus()).isEqualTo("FAILED");
        assertThat(wooCommerce.getLastSyncAt()).isBefore(OffsetDateTime.now().minusDays(1));
        assertThat(wooCommerce.getLastErrorMessage())
                .contains("WooCommerce")
                .contains("401")
                .contains("consumer key");
        assertThat(wooCommerce.getWebhookSecretEncrypted()).startsWith("v1.");
        assertThat(wooCommerce.getEncryptedApiCredentials()).startsWith("v1.");

        devDataSeeder.seed();
        assertThat(productRepository.count()).isEqualTo(productCount);
        assertThat(orderRepository.count()).isEqualTo(totalOrders);
        assertThat(stockMovementRepository.count()).isEqualTo(movementCount);
        assertThat(platformConnectionRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should seed low-stock, out-of-stock, and waiting-for-stock demo examples")
    void shouldSeedStockPressureExamples() {
        devDataSeeder.seed();

        ProductVariant lowStockVariant = productVariantRepository
                .findBySkuIgnoreCase("COF-PAN-100G")
                .orElseThrow();
        InventoryLevel lowStock = inventoryRepository
                .findByProductVariantId(lowStockVariant.getId())
                .orElseThrow();
        assertThat(lowStock.getAvailableQuantity())
                .isPositive()
                .isLessThanOrEqualTo(lowStockVariant.getMinStockThreshold());

        ProductVariant outOfStockVariant = productVariantRepository
                .findBySkuIgnoreCase("COF-PAN-200G")
                .orElseThrow();
        InventoryLevel outOfStock = inventoryRepository
                .findByProductVariantId(outOfStockVariant.getId())
                .orElseThrow();
        assertThat(outOfStock.getAvailableQuantity()).isZero();

        assertThat(orderRepository.findAll())
                .anyMatch(order -> order.getStatus() == OrderStatus.WAITING_FOR_STOCK);

        Integer reservedOnWaitingOrders = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(oi.reserved_quantity), 0)
                FROM order_items oi
                JOIN orders o ON o.id = oi.order_id
                WHERE o.status = 'WAITING_FOR_STOCK'
                """, Integer.class);
        assertThat(reservedOnWaitingOrders).isZero();

        Integer waitingOnOutOfStock = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM order_items oi
                JOIN orders o ON o.id = oi.order_id
                JOIN inventory_levels il ON il.product_variant_id = oi.product_variant_id
                WHERE o.status = 'WAITING_FOR_STOCK'
                  AND il.on_hand_quantity - il.reserved_quantity = 0
                """, Integer.class);
        assertThat(waitingOnOutOfStock).isPositive();
    }

    @Test
    @DisplayName("Should backdate seeded orders, shipments, returns, and movements across four months")
    void shouldBackdateSeedHistory() {
        devDataSeeder.seed();
        OffsetDateTime now = OffsetDateTime.now();

        List<Order> orders = orderRepository.findAll();
        assertThat(orders).anyMatch(order -> order.getCreatedAt().isBefore(now.minusMonths(3)));
        assertThat(orders).anyMatch(order -> order.getCreatedAt().isAfter(now.minusDays(7)));
        assertThat(orders.stream()
                .map(order -> YearMonth.from(order.getCreatedAt()))
                .distinct()
                .count()).isGreaterThanOrEqualTo(4);

        List<OffsetDateTime> shippedDates = orders.stream()
                .map(Order::getShippedAt)
                .filter(Objects::nonNull)
                .toList();
        assertThat(shippedDates).anyMatch(date -> date.isBefore(now.minusMonths(3)));
        assertThat(shippedDates).anyMatch(date -> date.isAfter(now.minusDays(14)));
        assertThat(shippedDates.stream()
                .map(YearMonth::from)
                .distinct()
                .count()).isGreaterThanOrEqualTo(4);

        List<StockMovement> movements = stockMovementRepository.findAll();
        assertThat(movements).anyMatch(movement -> movement.getCreatedAt().isBefore(now.minusMonths(3)));
        assertThat(movements).anyMatch(movement -> movement.getCreatedAt().isAfter(now.minusDays(7)));
        assertThat(movements.stream()
                .map(movement -> YearMonth.from(movement.getCreatedAt()))
                .distinct()
                .count()).isGreaterThanOrEqualTo(4);

        List<OrderReturn> returns = orderReturnRepository.findAll();
        assertThat(returns).isNotEmpty();
        assertThat(returns).allMatch(orderReturn ->
                orderReturn.getProcessedAt().isBefore(now.minusMonths(1)));
    }

    @Test
    @DisplayName("Should keep inventory, reservations, and the stock movement ledger reconciled")
    void shouldKeepInventoryLedgerConsistent() {
        devDataSeeder.seed();

        Map<UUID, int[]> inventory = new HashMap<>();
        jdbcTemplate.query("""
                SELECT product_variant_id, on_hand_quantity, reserved_quantity
                FROM inventory_levels
                """, rs -> {
            UUID variantId = rs.getObject("product_variant_id", UUID.class);
            int onHand = rs.getInt("on_hand_quantity");
            int reserved = rs.getInt("reserved_quantity");
            assertThat(onHand).isGreaterThanOrEqualTo(0);
            assertThat(reserved).isGreaterThanOrEqualTo(0);
            assertThat(onHand - reserved).isGreaterThanOrEqualTo(0);
            inventory.put(variantId, new int[]{onHand, reserved});
        });
        assertThat(inventory).isNotEmpty();

        Map<UUID, int[]> movementTotals = new HashMap<>();
        jdbcTemplate.query("""
                SELECT product_variant_id,
                       SUM(delta_on_hand) AS on_hand_delta,
                       SUM(delta_reserved) AS reserved_delta
                FROM stock_movements
                GROUP BY product_variant_id
                """, rs -> {
            movementTotals.put(
                    rs.getObject("product_variant_id", UUID.class),
                    new int[]{rs.getInt("on_hand_delta"), rs.getInt("reserved_delta")}
            );
        });
        assertThat(movementTotals.keySet())
                .containsExactlyInAnyOrderElementsOf(inventory.keySet());

        inventory.forEach((variantId, totals) -> {
            int[] ledgerTotals = movementTotals.get(variantId);
            assertThat(ledgerTotals[0])
                    .as("on-hand ledger for variant %s", variantId)
                    .isEqualTo(totals[0]);
            assertThat(ledgerTotals[1])
                    .as("reserved ledger for variant %s", variantId)
                    .isEqualTo(totals[1]);
        });

        Map<UUID, Integer> reservedByOpenOrders = new HashMap<>();
        jdbcTemplate.query("""
                SELECT oi.product_variant_id, SUM(oi.reserved_quantity) AS reserved
                FROM order_items oi
                JOIN orders o ON o.id = oi.order_id
                WHERE o.status IN ('PENDING', 'READY_TO_SHIP')
                GROUP BY oi.product_variant_id
                """, rs -> {
            reservedByOpenOrders.put(
                    rs.getObject("product_variant_id", UUID.class),
                    rs.getInt("reserved")
            );
        });
        inventory.forEach((variantId, totals) -> assertThat(totals[1])
                .as("orphan reservation check for variant %s", variantId)
                .isEqualTo(reservedByOpenOrders.getOrDefault(variantId, 0)));

        Integer orphanedReservations = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM order_items oi
                JOIN orders o ON o.id = oi.order_id
                WHERE o.status NOT IN ('PENDING', 'READY_TO_SHIP')
                  AND oi.reserved_quantity > 0
                """, Integer.class);
        assertThat(orphanedReservations).isZero();

        for (Order order : orderRepository.findAll()) {
            List<StockMovement> orderMovements = stockMovementRepository
                    .findAllByReferenceId(order.getId());

            if (order.getStatus() == OrderStatus.WAITING_FOR_STOCK) {
                assertThat(orderMovements)
                        .as("waiting order %s must not reserve stock", order.getOrderNumber())
                        .noneMatch(movement ->
                                movement.getReason() == StockMovementReason.ORDER_RESERVED);
            } else {
                assertThat(orderMovements)
                        .as("reservation movement for order %s", order.getOrderNumber())
                        .anyMatch(movement ->
                                movement.getReason() == StockMovementReason.ORDER_RESERVED);
            }

            if (order.getStatus() == OrderStatus.SHIPPED
                    || order.getStatus() == OrderStatus.RETURNED
                    || order.getStatus() == OrderStatus.PARTIALLY_RETURNED) {
                assertThat(orderMovements)
                        .as("shipment movement for order %s", order.getOrderNumber())
                        .anyMatch(movement ->
                                movement.getReason() == StockMovementReason.ORDER_SHIPPED);
            }

            if (order.getStatus() == OrderStatus.CANCELLED) {
                assertThat(orderMovements)
                        .as("cancellation movement for order %s", order.getOrderNumber())
                        .anyMatch(movement ->
                                movement.getReason() == StockMovementReason.ORDER_CANCELLED);
            }

            if (order.getStatus() == OrderStatus.RETURNED
                    || order.getStatus() == OrderStatus.PARTIALLY_RETURNED) {
                List<OrderReturn> returns = orderReturnRepository.findAllByOrderId(order.getId());
                assertThat(returns)
                        .as("return history for order %s", order.getOrderNumber())
                        .isNotEmpty();

                for (OrderReturn orderReturn : returns) {
                    assertThat(stockMovementRepository.findAllByReferenceId(orderReturn.getId()))
                            .as("return movement for order %s", order.getOrderNumber())
                            .anyMatch(movement ->
                                    movement.getReason() == StockMovementReason.RETURN_RESTOCK
                                            || movement.getReason() == StockMovementReason.RETURN_DAMAGED);
                }
            }
        }
    }

    @Test
    @DisplayName("Should restore the documented demo credentials and clear the forced-change flag on every seed")
    void shouldResetDemoCredentialsOnEverySeed() {
        devDataSeeder.seed();

        userRepository.findByEmailIgnoreCase("owner@stockledger.io").ifPresent(owner -> {
            owner.resetPassword(passwordEncoder.encode("BorrowedOwnerPass2026!"));
            userRepository.save(owner);
        });
        userRepository.findByEmailIgnoreCase("staff@stockledger.io").ifPresent(staff -> {
            staff.resetPassword(passwordEncoder.encode("BorrowedStaffPass2026!"));
            userRepository.save(staff);
        });

        devDataSeeder.seed();

        var owner = userRepository.findByEmailIgnoreCase("owner@stockledger.io").orElseThrow();
        assertThat(passwordEncoder.matches("OwnerPass2026!", owner.getPasswordHash())).isTrue();
        assertThat(owner.isMustChangePassword()).isFalse();

        var staff = userRepository.findByEmailIgnoreCase("staff@stockledger.io").orElseThrow();
        assertThat(passwordEncoder.matches("StaffPass2026!", staff.getPasswordHash())).isTrue();
        assertThat(staff.isMustChangePassword()).isFalse();
    }

    private void cleanDatabase() {
        orderReturnRepository.deleteAll();
        orderRepository.deleteAll();
        stockCountItemRepository.deleteAll();
        stockCountRepository.deleteAll();
        platformConnectionRepository.deleteAll();
        stockMovementRepository.deleteAll();
        inventoryRepository.deleteAll();
        productVariantRepository.deleteAll();
        productRepository.deleteAll();
        shippingPriceListRepository.deleteAll();
        userRepository.deleteAll();
    }
}
