package com.shvmpk.stock_ledger.integration.web;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import com.shvmpk.stock_ledger.order.entity.Order;
import com.shvmpk.stock_ledger.order.entity.OrderItem;
import com.shvmpk.stock_ledger.order.entity.OrderSource;
import com.shvmpk.stock_ledger.order.entity.OrderStatus;
import com.shvmpk.stock_ledger.order.repository.OrderRepository;
import com.shvmpk.stock_ledger.order.repository.OrderReturnRepository;
import com.shvmpk.stock_ledger.product.entity.Product;
import com.shvmpk.stock_ledger.product.entity.ProductVariant;
import com.shvmpk.stock_ledger.product.repository.ProductRepository;
import com.shvmpk.stock_ledger.product.repository.ProductVariantRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Order Read Model Integration Tests (Sorting, Timestamps & Status Summary)")
class OrderReadModelIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderReturnRepository orderReturnRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private ProductVariant hoodieVariant;

    @BeforeEach
    void setUp() {
        orderReturnRepository.deleteAll();
        orderRepository.deleteAll();
        productVariantRepository.deleteAll();
        productRepository.deleteAll();

        Product hoodie = Product.create(
                UUID.randomUUID(),
                "Heavyweight Cotton Hoodie",
                "450 GSM organic French terry cotton hoodie",
                "Apparel"
        );
        hoodieVariant = hoodie.addVariant(
                UUID.randomUUID(),
                "HOOD-NVY-READ",
                "5060123456791",
                "Color / Size",
                "Navy / XL",
                new BigDecimal("28.00"),
                new BigDecimal("68.00"),
                750,
                5
        );
        productRepository.saveAndFlush(hoodie);
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("Should list orders newest first with a stable tiebreaker and pagination metadata")
    void shouldListOrdersNewestFirstWithStableTiebreaker() throws Exception {
        Order first = createOrder(
                "ORD-READ-1001",
                "Astrid Lindqvist",
                "astrid.lindqvist@scandiboutique.se",
                "Kungsgatan 44, 111 35 Stockholm, Sweden"
        );
        Order second = createOrder(
                "ORD-READ-1002",
                "Freja Lindholm",
                "freja.lindholm@nordicliving.se",
                "Drottninggatan 12, 411 14 Gothenburg, Sweden"
        );
        Order third = createOrder(
                "ORD-READ-1003",
                "Lars Larsson",
                "lars.larsson@nordicwear.se",
                "Stortorget 12, 211 22 Malmo, Sweden"
        );
        orderRepository.saveAllAndFlush(List.of(first, second, third));

        OffsetDateTime sameInstant = OffsetDateTime.parse("2026-09-18T09:15:00Z");
        setCreatedAt(first.getId(), sameInstant);
        setCreatedAt(second.getId(), sameInstant);
        setCreatedAt(third.getId(), sameInstant.plusDays(1));
        entityManager.clear();

        List<String> tieOrderNumbers = List.of("ORD-READ-1001", "ORD-READ-1002");
        String tieWinnerOrderNumber = tieOrderNumbers.stream().max(String::compareTo).orElseThrow();
        String tieLoserOrderNumber = tieOrderNumbers.stream().min(String::compareTo).orElseThrow();

        mockMvc.perform(get("/api/v1/orders")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].orderNumber").value("ORD-READ-1003"))
                .andExpect(jsonPath("$.data[1].orderNumber").value(tieWinnerOrderNumber))
                .andExpect(jsonPath("$.pagination.page").value(0))
                .andExpect(jsonPath("$.pagination.size").value(2))
                .andExpect(jsonPath("$.pagination.totalElements").value(3))
                .andExpect(jsonPath("$.pagination.totalPages").value(2))
                .andExpect(jsonPath("$.pagination.hasMore").value(true));

        mockMvc.perform(get("/api/v1/orders")
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].orderNumber").value(tieLoserOrderNumber))
                .andExpect(jsonPath("$.pagination.hasMore").value(false));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("Should clamp page size and reject a negative page index")
    void shouldClampPageSizeAndRejectNegativePage() throws Exception {
        orderRepository.saveAndFlush(createOrder(
                "ORD-READ-1101",
                "Astrid Lindqvist",
                "astrid.lindqvist@scandiboutique.se",
                "Kungsgatan 44, 111 35 Stockholm, Sweden"
        ));

        mockMvc.perform(get("/api/v1/orders").param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.size").value(100));

        mockMvc.perform(get("/api/v1/orders").param("size", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.size").value(1));

        mockMvc.perform(get("/api/v1/orders").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.invalidParams[0].field").value("page"));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("Should expose shippedAt and returnedAt on get and list responses")
    void shouldExposeShippingAndReturnTimestamps() throws Exception {
        Order shipped = createOrder(
                "ORD-READ-2001",
                "Astrid Lindqvist",
                "astrid.lindqvist@scandiboutique.se",
                "Kungsgatan 44, 111 35 Stockholm, Sweden"
        );
        shipped.markShipped(
                "DHL",
                "MEDIUM",
                "1234567890",
                "https://www.dhl.com/en/express/tracking.html?AWB=1234567890",
                new BigDecimal("6.50")
        );

        Order returned = createOrder(
                "ORD-READ-2002",
                "Freja Lindholm",
                "freja.lindholm@nordicliving.se",
                "Drottninggatan 12, 411 14 Gothenburg, Sweden"
        );
        returned.markShipped(
                "DHL",
                "SMALL",
                "9876543210",
                "https://www.dhl.com/en/express/tracking.html?AWB=9876543210",
                new BigDecimal("5.25")
        );
        returned.markReturned();

        orderRepository.saveAllAndFlush(List.of(shipped, returned));
        entityManager.clear();

        mockMvc.perform(get("/api/v1/orders/" + shipped.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shippedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.returnedAt").value(nullValue()));

        mockMvc.perform(get("/api/v1/orders/" + returned.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shippedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.returnedAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/orders").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].orderNumber").value("ORD-READ-2002"))
                .andExpect(jsonPath("$.data[0].shippedAt").isNotEmpty())
                .andExpect(jsonPath("$.data[0].returnedAt").isNotEmpty())
                .andExpect(jsonPath("$.data[1].orderNumber").value("ORD-READ-2001"))
                .andExpect(jsonPath("$.data[1].shippedAt").isNotEmpty())
                .andExpect(jsonPath("$.data[1].returnedAt").value(nullValue()));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("Should return zero-filled status summary for an empty order ledger")
    void shouldReturnZeroFilledStatusSummaryWhenEmpty() throws Exception {
        mockMvc.perform(get("/api/v1/orders/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(OrderStatus.values().length))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data[0].count").value(0))
                .andExpect(jsonPath("$.data[1].status").value("WAITING_FOR_STOCK"))
                .andExpect(jsonPath("$.data[1].count").value(0))
                .andExpect(jsonPath("$.data[2].status").value("READY_TO_SHIP"))
                .andExpect(jsonPath("$.data[2].count").value(0))
                .andExpect(jsonPath("$.data[3].status").value("SHIPPED"))
                .andExpect(jsonPath("$.data[3].count").value(0))
                .andExpect(jsonPath("$.data[4].status").value("RETURNED"))
                .andExpect(jsonPath("$.data[4].count").value(0))
                .andExpect(jsonPath("$.data[5].status").value("PARTIALLY_RETURNED"))
                .andExpect(jsonPath("$.data[5].count").value(0))
                .andExpect(jsonPath("$.data[6].status").value("CANCELLED"))
                .andExpect(jsonPath("$.data[6].count").value(0));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("Should return exact status counts for a mixed order ledger")
    void shouldReturnExactStatusCountsForMixedOrders() throws Exception {
        Order pending = createOrder(
                "ORD-READ-3001",
                "Astrid Lindqvist",
                "astrid.lindqvist@scandiboutique.se",
                "Kungsgatan 44, 111 35 Stockholm, Sweden"
        );
        Order anotherPending = createOrder(
                "ORD-READ-3002",
                "Freja Lindholm",
                "freja.lindholm@nordicliving.se",
                "Drottninggatan 12, 411 14 Gothenburg, Sweden"
        );
        Order waiting = createOrder(
                "ORD-READ-3003",
                "Lars Larsson",
                "lars.larsson@nordicwear.se",
                "Stortorget 12, 211 22 Malmo, Sweden"
        );
        waiting.markWaitingForStock();
        Order ready = createOrder(
                "ORD-READ-3004",
                "Elin Bergstrom",
                "elin.bergstrom@fjallravenoutlet.se",
                "Vastra Hamngatan 8, 411 17 Gothenburg, Sweden"
        );
        ready.updateStatus(OrderStatus.READY_TO_SHIP);
        Order shipped = createOrder(
                "ORD-READ-3005",
                "Oskar Nilsson",
                "oskar.nilsson@nordicgear.se",
                "Sveavagen 21, 111 34 Stockholm, Sweden"
        );
        shipped.markShipped(
                "DHL",
                "MEDIUM",
                "1234567890",
                "https://www.dhl.com/en/express/tracking.html?AWB=1234567890",
                new BigDecimal("6.50")
        );
        Order returned = createOrder(
                "ORD-READ-3006",
                "Maja Svensson",
                "maja.svensson@scandihome.se",
                "Ostra Storgatan 5, 553 21 Jonkoping, Sweden"
        );
        returned.markShipped(
                "DHL",
                "SMALL",
                "9876543210",
                "https://www.dhl.com/en/express/tracking.html?AWB=9876543210",
                new BigDecimal("5.25")
        );
        returned.markReturned();
        Order partiallyReturned = createOrder(
                "ORD-READ-3007",
                "Hugo Andersson",
                "hugo.andersson@nordicliving.se",
                "Norra Kungsgatan 3, 802 50 Gavle, Sweden"
        );
        partiallyReturned.markShipped(
                "DHL",
                "LARGE",
                "5556667778",
                "https://www.dhl.com/en/express/tracking.html?AWB=5556667778",
                new BigDecimal("7.75")
        );
        partiallyReturned.markPartiallyReturned();
        Order cancelled = createOrder(
                "ORD-READ-3008",
                "Saga Johansson",
                "saga.johansson@fjallbackaoutlet.se",
                "Langgatan 18, 457 40 Fjallbacka, Sweden"
        );
        cancelled.updateStatus(OrderStatus.CANCELLED);

        orderRepository.saveAllAndFlush(List.of(
                pending,
                anotherPending,
                waiting,
                ready,
                shipped,
                returned,
                partiallyReturned,
                cancelled
        ));

        mockMvc.perform(get("/api/v1/orders/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data[0].count").value(2))
                .andExpect(jsonPath("$.data[1].status").value("WAITING_FOR_STOCK"))
                .andExpect(jsonPath("$.data[1].count").value(1))
                .andExpect(jsonPath("$.data[2].status").value("READY_TO_SHIP"))
                .andExpect(jsonPath("$.data[2].count").value(1))
                .andExpect(jsonPath("$.data[3].status").value("SHIPPED"))
                .andExpect(jsonPath("$.data[3].count").value(1))
                .andExpect(jsonPath("$.data[4].status").value("RETURNED"))
                .andExpect(jsonPath("$.data[4].count").value(1))
                .andExpect(jsonPath("$.data[5].status").value("PARTIALLY_RETURNED"))
                .andExpect(jsonPath("$.data[5].count").value(1))
                .andExpect(jsonPath("$.data[6].status").value("CANCELLED"))
                .andExpect(jsonPath("$.data[6].count").value(1));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("Should allow OWNER to read the status summary and order list")
    void shouldAllowOwnerToReadOrderReadModels() throws Exception {
        mockMvc.perform(get("/api/v1/orders/summary"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should reject unauthenticated order read model requests")
    void shouldRejectUnauthenticatedReadModelRequests() throws Exception {
        mockMvc.perform(get("/api/v1/orders/summary"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized());
    }

    private Order createOrder(String orderNumber, String customerName, String email, String address) {
        Order order = Order.create(
                orderNumber,
                OrderSource.MANUAL,
                null,
                customerName,
                email,
                address,
                new BigDecimal("4.99")
        );
        order.addItem(OrderItem.createUnreserved(
                order,
                hoodieVariant,
                1,
                new BigDecimal("68.00")
        ));
        return order;
    }

    private void setCreatedAt(UUID orderId, OffsetDateTime createdAt) {
        jdbcTemplate.update(
                "UPDATE orders SET created_at = ? WHERE id = ?",
                Timestamp.from(createdAt.toInstant()),
                orderId
        );
    }
}
