package com.shvmpk.stock_ledger.integration.web;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import com.shvmpk.stock_ledger.inventory.entity.InventoryLevel;
import com.shvmpk.stock_ledger.inventory.repository.InventoryRepository;
import com.shvmpk.stock_ledger.inventory.repository.StockMovementRepository;
import com.shvmpk.stock_ledger.platform.entity.PlatformConnection;
import com.shvmpk.stock_ledger.platform.entity.PlatformType;
import com.shvmpk.stock_ledger.platform.repository.PlatformConnectionRepository;
import com.shvmpk.stock_ledger.product.entity.Product;
import com.shvmpk.stock_ledger.product.entity.ProductVariant;
import com.shvmpk.stock_ledger.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
@DisplayName("StockController & Platform List Integration Tests (Task 4)")
class StockControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private PlatformConnectionRepository platformConnectionRepository;

    @Autowired
    private com.shvmpk.stock_ledger.order.repository.OrderReturnRepository orderReturnRepository;

    @Autowired
    private com.shvmpk.stock_ledger.order.repository.OrderRepository orderRepository;

    private MockMvc mockMvc;
    private ProductVariant variant;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        orderReturnRepository.deleteAll();
        orderRepository.deleteAll();
        platformConnectionRepository.deleteAll();
        stockMovementRepository.deleteAll();
        inventoryRepository.deleteAll();
        productRepository.deleteAll();

        Product product = Product.create(UUID.randomUUID(), "Organic Heavyweight Hoodie", "Loopback terry", "Apparel");
        variant = product.addVariant(
                UUID.randomUUID(),
                "HOOD-TEST-NVY-M",
                "5060123888001",
                "Size / Color",
                "M / Navy",
                new BigDecimal("22.00"),
                new BigDecimal("65.00"),
                620,
                5
        );
        productRepository.save(product);
        inventoryRepository.save(InventoryLevel.initialize(UUID.randomUUID(), variant, 10));

        PlatformConnection conn = PlatformConnection.create(
                UUID.randomUUID(),
                PlatformType.SHOPIFY,
                "https://nordic-store.myshopify.com/admin/api/2026-01",
                "secret123",
                "token_abc"
        );
        platformConnectionRepository.save(conn);
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("POST /api/v1/stock/receive should increase on-hand quantity and log audit movement")
    void shouldReceiveStockSuccessfully() throws Exception {
        String payload = """
                {
                    "productVariantId": "%s",
                    "quantity": 25,
                    "notes": "Inbound shipment from supplier"
                }
                """.formatted(variant.getId());

        mockMvc.perform(post("/api/v1/stock/receive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.stockLevel.onHandQuantity").value(35))
                .andExpect(jsonPath("$.data.stockLevel.availableQuantity").value(35))
                .andExpect(jsonPath("$.data.allocatedOrderNumbers").isEmpty());

        InventoryLevel updated = inventoryRepository.findByProductVariantId(variant.getId()).orElseThrow();
        assertThat(updated.getOnHandQuantity()).isEqualTo(35);
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("POST /api/v1/stock/count should adjust physical count and return audit variance result")
    void shouldPerformPhysicalStockCount() throws Exception {
        String payload = """
                {
                    "productVariantId": "%s",
                    "countedOnHand": 8,
                    "notes": "Shelf count audit"
                }
                """.formatted(variant.getId());

        mockMvc.perform(post("/api/v1/stock/count")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.variance").value(-2))
                .andExpect(jsonPath("$.data.hasReservationDeficit").value(false));

        InventoryLevel updated = inventoryRepository.findByProductVariantId(variant.getId()).orElseThrow();
        assertThat(updated.getOnHandQuantity()).isEqualTo(8);
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("GET /api/v1/stock/movements should return paginated movements ledger")
    void shouldFetchStockMovements() throws Exception {
        // First trigger a receive to create a movement
        String payload = """
                {
                    "productVariantId": "%s",
                    "quantity": 10,
                    "notes": "Stock receipt"
                }
                """.formatted(variant.getId());

        mockMvc.perform(post("/api/v1/stock/receive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/stock/movements")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].productName").value("Organic Heavyweight Hoodie"))
                .andExpect(jsonPath("$.data[0].sku").value("HOOD-TEST-NVY-M"))
                .andExpect(jsonPath("$.pagination.page").value(0))
                .andExpect(jsonPath("$.pagination.size").value(1))
                .andExpect(jsonPath("$.pagination.totalElements").value(1))
                .andExpect(jsonPath("$.pagination.totalPages").value(1))
                .andExpect(jsonPath("$.pagination.hasMore").value(false));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("GET /api/v1/platforms should be forbidden for STAFF (403)")
    void staffShouldBeForbiddenFromPlatforms() throws Exception {
        mockMvc.perform(get("/api/v1/platforms"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("GET /api/v1/platforms should return connected platform stores for OWNER")
    void ownerCanFetchPlatforms() throws Exception {
        mockMvc.perform(get("/api/v1/platforms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].platformType").value("SHOPIFY"))
                .andExpect(jsonPath("$.data[0].syncStatus").value("PENDING"));
    }
}
