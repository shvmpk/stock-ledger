package com.shvmpk.stock_ledger.integration.service;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import com.shvmpk.stock_ledger.analytics.dto.ReorderDetailResponse;
import com.shvmpk.stock_ledger.analytics.dto.ReorderHistoryStatus;
import com.shvmpk.stock_ledger.analytics.dto.ReorderItemResponse;
import com.shvmpk.stock_ledger.analytics.dto.ReorderListResponse;
import com.shvmpk.stock_ledger.analytics.service.ReorderService;
import com.shvmpk.stock_ledger.inventory.entity.InventoryLevel;
import com.shvmpk.stock_ledger.inventory.repository.InventoryRepository;
import com.shvmpk.stock_ledger.order.entity.ItemCondition;
import com.shvmpk.stock_ledger.order.entity.Order;
import com.shvmpk.stock_ledger.order.entity.OrderItem;
import com.shvmpk.stock_ledger.order.entity.OrderReturn;
import com.shvmpk.stock_ledger.order.entity.OrderReturnItem;
import com.shvmpk.stock_ledger.order.entity.OrderSource;
import com.shvmpk.stock_ledger.order.entity.OrderStatus;
import com.shvmpk.stock_ledger.order.repository.OrderRepository;
import com.shvmpk.stock_ledger.order.repository.OrderReturnRepository;
import com.shvmpk.stock_ledger.product.entity.Product;
import com.shvmpk.stock_ledger.product.entity.ProductVariant;
import com.shvmpk.stock_ledger.product.repository.ProductRepository;
import com.shvmpk.stock_ledger.settings.service.ReorderSettingsService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
@DisplayName("Reorder Service Integration Tests (Task 3.1)")
class ReorderServiceIntegrationTest {

    @Autowired
    private ReorderService reorderService;

    @Autowired
    private ReorderSettingsService reorderSettingsService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderReturnRepository orderReturnRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("Builds one urgency-sorted list from sold, returned, sellable, and retired stock")
    void shouldBuildReorderListFromSalesAndReturns() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        ProductVariant hoodie = createVariant(
                "Heavyweight Cotton Hoodie",
                "HOOD-NVY-XL",
                "5901234123457",
                8,
                5,
                5,
                false
        );
        ProductVariant coffee = createVariant(
                "Ethiopia Guji Coffee 250g",
                "COF-ETH-250G",
                "5012345678900",
                0,
                0,
                null,
                false
        );
        ProductVariant grinder = createVariant(
                "Handground Coffee Grinder",
                "GRND-HAND-SS",
                "4006381333931",
                10,
                0,
                2,
                false
        );
        createVariant(
                "Stoneware Pour-Over Set",
                "POUR-SET-SND",
                "9780201379624",
                100,
                0,
                null,
                false
        );
        createVariant(
                "Waxed Canvas Field Jacket",
                "JKT-WAX-OLV-M",
                "5060123456819",
                0,
                0,
                5,
                true
        );

        createShippedOrder("ORD-2026-1042", hoodie, 40, now.minusDays(60));
        Order recentHoodieOrder = createShippedOrder("ORD-2026-1078", hoodie, 20, now.minusDays(10));
        createReturn(recentHoodieOrder, hoodie, 6, now.minusDays(5));

        createShippedOrder("ORD-2026-1051", grinder, 30, now.minusDays(70));
        createShippedOrder("ORD-2026-1063", grinder, 30, now.minusDays(40));
        createShippedOrder("ORD-2026-1090", grinder, 30, now.minusDays(30));

        reloadFromDatabase();
        ReorderListResponse response = reorderService.getReorderList();

        assertThat(response.salesWindowDays()).isEqualTo(90);
        assertThat(response.leadTimeDays()).isEqualTo(14);
        assertThat(response.targetCoverDays()).isEqualTo(14);
        assertThat(response.minimumHistoryDays()).isEqualTo(21);
        assertThat(response.minimumUnitsSold()).isEqualTo(5);
        assertThat(response.generatedAt()).isNotNull();
        assertThat(response.items())
                .extracting(ReorderItemResponse::sku)
                .as("Out of stock first, then the shortest cover")
                .containsExactly("COF-ETH-250G", "HOOD-NVY-XL", "GRND-HAND-SS");

        ReorderItemResponse hoodieItem = itemFor(response, "HOOD-NVY-XL");
        assertThat(hoodieItem.productName()).isEqualTo("Heavyweight Cotton Hoodie");
        assertThat(hoodieItem.optionName()).isEqualTo("Size");
        assertThat(hoodieItem.optionValue()).isEqualTo("XL");
        assertThat(hoodieItem.barcode()).isEqualTo("5901234123457");
        assertThat(hoodieItem.onHand()).isEqualTo(8);
        assertThat(hoodieItem.reserved()).isEqualTo(5);
        assertThat(hoodieItem.available()).isEqualTo(3);
        assertThat(hoodieItem.reorderLevel()).isEqualTo(5);
        assertThat(hoodieItem.unitsSold())
                .as("Sixty shipped less six returned")
                .isEqualTo(54);
        assertThat(hoodieItem.ordersCount()).isEqualTo(2);
        assertThat(hoodieItem.daysOfCover()).isEqualTo(5);
        assertThat(hoodieItem.suggestedQuantity()).isEqualTo(14);
        assertThat(hoodieItem.historyStatus()).isEqualTo(ReorderHistoryStatus.SUFFICIENT);

        ReorderItemResponse coffeeItem = itemFor(response, "COF-ETH-250G");
        assertThat(coffeeItem.available()).isZero();
        assertThat(coffeeItem.historyStatus()).isEqualTo(ReorderHistoryStatus.INSUFFICIENT);
        assertThat(coffeeItem.daysOfCover()).isNull();
        assertThat(coffeeItem.suggestedQuantity())
                .as("An out-of-stock item without sales history must not invent a quantity")
                .isNull();

        ReorderItemResponse grinderItem = itemFor(response, "GRND-HAND-SS");
        assertThat(grinderItem.daysOfCover()).isEqualTo(10);
        assertThat(grinderItem.suggestedQuantity()).isEqualTo(18);
    }

    @Test
    @DisplayName("Says there is not enough history instead of inventing an estimate")
    void shouldReportInsufficientHistory() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        ProductVariant young = createVariant(
                "Merino Wool Beanie",
                "BEANIE-CHAR-OS",
                "5901234123457",
                2,
                0,
                5,
                false
        );
        ProductVariant thin = createVariant(
                "Linen Tea Towel",
                "TOWEL-LIN-NAT",
                "5012345678900",
                2,
                0,
                5,
                false
        );

        createShippedOrder("ORD-2026-1101", young, 50, now.minusDays(10));
        createShippedOrder("ORD-2026-1102", thin, 4, now.minusDays(60));

        reloadFromDatabase();
        ReorderListResponse response = reorderService.getReorderList();

        assertThat(response.items()).hasSize(2);
        assertThat(response.items())
                .allSatisfy(item -> {
                    assertThat(item.historyStatus()).isEqualTo(ReorderHistoryStatus.INSUFFICIENT);
                    assertThat(item.daysOfCover()).isNull();
                    assertThat(item.suggestedQuantity()).isNull();
                });
    }

    @Test
    @DisplayName("Traces the estimate to the shipped orders and returns inside the window")
    void shouldTraceEstimateToSalesLines() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        ProductVariant hoodie = createVariant(
                "Heavyweight Cotton Hoodie",
                "HOOD-NVY-XL",
                "5901234123457",
                8,
                5,
                5,
                false
        );

        createShippedOrder("ORD-2026-1042", hoodie, 40, now.minusDays(60));
        Order recentOrder = createShippedOrder("ORD-2026-1078", hoodie, 20, now.minusDays(10));
        createReturn(recentOrder, hoodie, 6, now.minusDays(5));
        createShippedOrder("ORD-2026-0981", hoodie, 30, now.minusDays(120));

        reloadFromDatabase();
        ReorderDetailResponse detail = reorderService.getDetail(hoodie.getId());

        assertThat(detail.sku()).isEqualTo("HOOD-NVY-XL");
        assertThat(detail.productName()).isEqualTo("Heavyweight Cotton Hoodie");
        assertThat(detail.salesWindowDays()).isEqualTo(90);
        assertThat(detail.unitsShipped()).isEqualTo(60);
        assertThat(detail.unitsReturned()).isEqualTo(6);
        assertThat(detail.netUnitsSold()).isEqualTo(54);
        assertThat(detail.sales())
                .extracting(line -> line.orderNumber())
                .containsExactly("ORD-2026-1078", "ORD-2026-1042");
        assertThat(detail.returns())
                .extracting(line -> line.orderNumber())
                .containsExactly("ORD-2026-1078");
        assertThat(detail.returns().getFirst().quantity()).isEqualTo(6);
        assertThat(detail.returns().getFirst().condition()).isEqualTo("SELLABLE");
    }

    @Test
    @DisplayName("Ignores pending and cancelled orders when measuring demand")
    void shouldIgnoreUnshippedAndCancelledOrders() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        ProductVariant variant = createVariant(
                "Stoneware Pour-Over Set",
                "POUR-SET-SND",
                "9780201379624",
                0,
                0,
                5,
                false
        );

        Order pending = Order.create(
                "ORD-2026-1201",
                OrderSource.MANUAL,
                null,
                "Harriet Wells",
                "harriet.wells@example.co.uk",
                "18 Marlborough Crescent\nBristol BS8 2LT\nUnited Kingdom",
                new BigDecimal("4.95")
        );
        pending.addItem(OrderItem.create(pending, variant, 100, variant.getSellingPrice()));
        orderRepository.saveAndFlush(pending);

        Order cancelled = createShippedOrder("ORD-2026-1202", variant, 100, now.minusDays(20));
        cancelled.updateStatus(OrderStatus.CANCELLED);
        orderRepository.saveAndFlush(cancelled);

        reloadFromDatabase();
        ReorderItemResponse item = itemFor(reorderService.getReorderList(), "POUR-SET-SND");

        assertThat(item.unitsSold()).isZero();
        assertThat(item.ordersCount()).isZero();
        assertThat(item.historyStatus()).isEqualTo(ReorderHistoryStatus.INSUFFICIENT);
    }

    @Test
    @DisplayName("Applies the owner-adjusted lead time to the suggested quantity")
    void shouldApplyAdjustedLeadTime() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        ProductVariant hoodie = createVariant(
                "Heavyweight Cotton Hoodie",
                "HOOD-NVY-XL",
                "5901234123457",
                8,
                5,
                5,
                false
        );
        createShippedOrder("ORD-2026-1042", hoodie, 40, now.minusDays(60));
        Order recentHoodieOrder = createShippedOrder("ORD-2026-1078", hoodie, 20, now.minusDays(10));
        createReturn(recentHoodieOrder, hoodie, 6, now.minusDays(5));

        reorderSettingsService.updateLeadTimeDays(21, null);
        reloadFromDatabase();
        ReorderListResponse response = reorderService.getReorderList();

        assertThat(response.leadTimeDays()).isEqualTo(21);
        assertThat(itemFor(response, "HOOD-NVY-XL").suggestedQuantity()).isEqualTo(18);
    }

    private void reloadFromDatabase() {
        entityManager.flush();
        entityManager.clear();
    }

    private ReorderItemResponse itemFor(ReorderListResponse response, String sku) {
        return response.items().stream()
                .filter(item -> item.sku().equals(sku))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No reorder item for " + sku));
    }

    private ProductVariant createVariant(
            String productName,
            String sku,
            String barcode,
            int onHand,
            int reserved,
            Integer reorderLevel,
            boolean retired
    ) {
        Product product = Product.create(
                UUID.randomUUID(),
                productName,
                "High-fidelity test catalogue item",
                "Apparel"
        );
        ProductVariant variant = product.addVariant(
                UUID.randomUUID(),
                sku,
                barcode,
                "Size",
                "XL",
                new BigDecimal("18.00"),
                new BigDecimal("58.00"),
                620,
                reorderLevel
        );
        if (retired) {
            product.retire();
        }
        productRepository.saveAndFlush(product);

        inventoryRepository.saveAndFlush(InventoryLevel.initialize(UUID.randomUUID(), variant, onHand));
        if (reserved > 0) {
            jdbcTemplate.update(
                    "UPDATE inventory_levels SET reserved_quantity = ? WHERE product_variant_id = ?",
                    reserved,
                    variant.getId()
            );
        }
        return variant;
    }

    private Order createShippedOrder(
            String orderNumber,
            ProductVariant variant,
            int quantity,
            OffsetDateTime shippedAt
    ) {
        Order order = Order.create(
                orderNumber,
                OrderSource.MANUAL,
                null,
                "Harriet Wells",
                "harriet.wells@example.co.uk",
                "18 Marlborough Crescent\nBristol BS8 2LT\nUnited Kingdom",
                new BigDecimal("5.80")
        );
        order.addItem(OrderItem.create(order, variant, quantity, variant.getSellingPrice()));
        order.markShipped(
                "DHL",
                "MEDIUM",
                "1234567890",
                "https://track.dhl.com/1234567890",
                new BigDecimal("6.40")
        );
        Order saved = orderRepository.saveAndFlush(order);
        jdbcTemplate.update(
                "UPDATE orders SET shipped_at = ?, updated_at = ? WHERE id = ?",
                shippedAt,
                shippedAt,
                saved.getId()
        );
        return saved;
    }

    private void createReturn(
            Order order,
            ProductVariant variant,
            int quantity,
            OffsetDateTime processedAt
    ) {
        OrderReturn orderReturn = OrderReturn.create(
                order,
                null,
                "Wrong size",
                "Customer returned an unworn hoodie"
        );
        orderReturn.addItem(OrderReturnItem.create(
                orderReturn,
                order.getItems().getFirst(),
                variant,
                quantity,
                ItemCondition.SELLABLE,
                true
        ));
        OrderReturn saved = orderReturnRepository.saveAndFlush(orderReturn);
        jdbcTemplate.update(
                "UPDATE order_returns SET processed_at = ? WHERE id = ?",
                processedAt,
                saved.getId()
        );
    }
}
