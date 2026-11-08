package com.shvmpk.stock_ledger.integration.web;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import com.shvmpk.stock_ledger.inventory.entity.InventoryLevel;
import com.shvmpk.stock_ledger.inventory.entity.StockMovementReason;
import com.shvmpk.stock_ledger.inventory.repository.InventoryRepository;
import com.shvmpk.stock_ledger.order.entity.Order;
import com.shvmpk.stock_ledger.order.entity.OrderItem;
import com.shvmpk.stock_ledger.order.entity.OrderSource;
import com.shvmpk.stock_ledger.order.entity.OrderStatus;
import com.shvmpk.stock_ledger.order.repository.OrderRepository;
import com.shvmpk.stock_ledger.product.entity.Product;
import com.shvmpk.stock_ledger.product.entity.ProductVariant;
import com.shvmpk.stock_ledger.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
@DisplayName("Stock Explanation Endpoint Integration Tests")
class StockExplanationControllerTest {

    private static final String SKU = "HOOD-EXP-NVY-XL";
    private static final String PRODUCT_NAME = "Heavyweight Cotton Hoodie";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private ProductVariant variant;
    private Order pendingOrder;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        Product product = Product.create(
                UUID.randomUUID(),
                PRODUCT_NAME,
                "Loopback terry with a brushed interior",
                "Apparel"
        );
        variant = product.addVariant(
                UUID.randomUUID(),
                SKU,
                "5060123999001",
                "Size / Color",
                "XL / Navy",
                new BigDecimal("24.00"),
                new BigDecimal("58.00"),
                620,
                18
        );
        productRepository.saveAndFlush(product);
        inventoryRepository.saveAndFlush(InventoryLevel.initialize(UUID.randomUUID(), variant, 8));

        pendingOrder = order(
                "ORD-2026-2042",
                OrderStatus.PENDING,
                "Freja Lindqvist",
                "Kungsgatan 14\n111 43 Stockholm\nSweden",
                4,
                true
        );
        order(
                "ORD-2026-2043",
                OrderStatus.READY_TO_SHIP,
                "Sofia Almeida",
                "7 Rua da Boavista\n4050-107 Porto\nPortugal",
                2,
                true
        );
        order(
                "ORD-2026-2039",
                OrderStatus.SHIPPED,
                "Nadia Okafor",
                "91 Deansgate\nManchester M3 2BW\nUnited Kingdom",
                3,
                false
        );

        // Reserve through the same atomic path production uses; the native update
        // clears the persistence context so the explanation reads the current row.
        inventoryRepository.atomicReserveStock(variant.getId(), 6);

        OffsetDateTime now = OffsetDateTime.now();
        insertMovement(StockMovementReason.RECEIVE, "MANUAL_RESTOCK", null, 10, 0, now.minusDays(3));
        insertMovement(StockMovementReason.ORDER_RESERVED, "ORDER", pendingOrder.getId(), 0, 4, now.minusDays(2));
        insertMovement(StockMovementReason.ORDER_RESERVED, "ORDER", null, 0, 2, now.minusDays(2).plusHours(1));
        insertMovement(StockMovementReason.ORDER_SHIPPED, "ORDER", null, -3, -3, now.minusDays(1));
        insertMovement(StockMovementReason.AUDIT_CORRECTION, "STOCK_COUNT", null, -1, 0, now.minusDays(120));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("Staff can read the figures, reservations, recent movement summary, and plain-language sentences")
    void staffCanReadTheExplanation() throws Exception {
        mockMvc.perform(get("/api/v1/stock/variants/{variantId}/explanation", variant.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.productVariantId").value(variant.getId().toString()))
                .andExpect(jsonPath("$.data.sku").value(SKU))
                .andExpect(jsonPath("$.data.productName").value(PRODUCT_NAME))
                .andExpect(jsonPath("$.data.onHandQuantity").value(8))
                .andExpect(jsonPath("$.data.reservedQuantity").value(6))
                .andExpect(jsonPath("$.data.availableQuantity").value(2))
                .andExpect(jsonPath("$.data.reorderLevel").value(18))
                .andExpect(jsonPath("$.data.sellable").value(true))
                .andExpect(jsonPath("$.data.movementWindowDays").value(90))
                .andExpect(jsonPath("$.data.reservations", hasSize(2)))
                .andExpect(jsonPath("$.data.reservations[0].orderNumber").value("ORD-2026-2042"))
                .andExpect(jsonPath("$.data.reservations[0].reservedQuantity").value(4))
                .andExpect(jsonPath("$.data.reservations[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data.reservations[1].orderNumber").value("ORD-2026-2043"))
                .andExpect(jsonPath("$.data.recentMovements", hasSize(3)))
                .andExpect(jsonPath("$.data.recentMovements[0].reason").value("ORDER_SHIPPED"))
                .andExpect(jsonPath("$.data.recentMovements[0].movementCount").value(1))
                .andExpect(jsonPath("$.data.recentMovements[0].netOnHand").value(-3))
                .andExpect(jsonPath("$.data.recentMovements[1].reason").value("ORDER_RESERVED"))
                .andExpect(jsonPath("$.data.recentMovements[1].movementCount").value(2))
                .andExpect(jsonPath("$.data.recentMovements[1].netReserved").value(6))
                .andExpect(jsonPath("$.data.recentMovements[2].reason").value("RECEIVE"))
                .andExpect(jsonPath("$.data.sentences[0]").value(
                        "There are 8 units on the shelf, 6 reserved for open orders, leaving 2 available to sell."
                ))
                .andExpect(jsonPath("$.data.sentences[1]", containsString("ORD-2026-2042 (4)")))
                .andExpect(jsonPath("$.data.sentences[2]", containsString("Latest movement")));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("Owner can also read the explanation")
    void ownerCanReadTheExplanation() throws Exception {
        mockMvc.perform(get("/api/v1/stock/variants/{variantId}/explanation", variant.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sku").value(SKU));
    }

    @Test
    @DisplayName("The explanation endpoint requires authentication")
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/stock/variants/{variantId}/explanation", variant.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("An unknown variant returns RFC 7807 404")
    void unknownVariantReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/stock/variants/{variantId}/explanation", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private Order order(
            String orderNumber,
            OrderStatus status,
            String customerName,
            String address,
            int quantity,
            boolean reserved
    ) {
        Order order = Order.create(
                orderNumber,
                OrderSource.MANUAL,
                null,
                customerName,
                "customer@example.co.uk",
                address,
                new BigDecimal("5.80")
        );
        order.updateStatus(status);
        OrderItem item = reserved
                ? OrderItem.create(order, variant, quantity, new BigDecimal("58.00"))
                : OrderItem.createUnreserved(order, variant, quantity, new BigDecimal("58.00"));
        order.addItem(item);
        return orderRepository.saveAndFlush(order);
    }

    private void insertMovement(
            StockMovementReason reason,
            String referenceType,
            UUID referenceId,
            int deltaOnHand,
            int deltaReserved,
            OffsetDateTime createdAt
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO stock_movements
                    (id, product_variant_id, sku, product_name, delta_on_hand, delta_reserved, reason,
                     reference_type, reference_id, actor_id, notes, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?)
                """,
                UUID.randomUUID(),
                variant.getId(),
                SKU,
                PRODUCT_NAME,
                deltaOnHand,
                deltaReserved,
                reason.name(),
                referenceType,
                referenceId,
                "Explanation fixture movement",
                createdAt
        );
    }
}
