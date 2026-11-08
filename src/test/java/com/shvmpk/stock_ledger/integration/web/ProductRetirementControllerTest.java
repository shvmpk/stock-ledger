package com.shvmpk.stock_ledger.integration.web;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import com.shvmpk.stock_ledger.common.error.RetiredProductException;
import com.shvmpk.stock_ledger.inventory.entity.InventoryLevel;
import com.shvmpk.stock_ledger.inventory.entity.StockMovement;
import com.shvmpk.stock_ledger.inventory.entity.StockMovementReason;
import com.shvmpk.stock_ledger.inventory.repository.InventoryRepository;
import com.shvmpk.stock_ledger.inventory.repository.StockMovementRepository;
import com.shvmpk.stock_ledger.inventory.service.InventoryService;
import com.shvmpk.stock_ledger.inventory.service.StockReceivingService;
import com.shvmpk.stock_ledger.order.dto.CreateOrderItemRequest;
import com.shvmpk.stock_ledger.order.dto.CreateOrderRequest;
import com.shvmpk.stock_ledger.order.dto.OrderResponse;
import com.shvmpk.stock_ledger.order.repository.OrderRepository;
import com.shvmpk.stock_ledger.order.repository.OrderReturnRepository;
import com.shvmpk.stock_ledger.order.service.OrderService;
import com.shvmpk.stock_ledger.product.entity.Product;
import com.shvmpk.stock_ledger.product.entity.ProductCatalogChangeType;
import com.shvmpk.stock_ledger.product.entity.ProductCatalogEvent;
import com.shvmpk.stock_ledger.product.entity.ProductCatalogScope;
import com.shvmpk.stock_ledger.product.entity.ProductVariant;
import com.shvmpk.stock_ledger.product.repository.ProductCatalogEventRepository;
import com.shvmpk.stock_ledger.product.repository.ProductRepository;
import com.shvmpk.stock_ledger.product.repository.ProductVariantRepository;
import com.shvmpk.stock_ledger.user.entity.Role;
import com.shvmpk.stock_ledger.user.entity.User;
import com.shvmpk.stock_ledger.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
@DisplayName("Product retirement")
class ProductRetirementControllerTest {

    private static final String OWNER_EMAIL = "elin.sandberg@northwind-supply.co";
    private static final String BCRYPT_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private ProductCatalogEventRepository productCatalogEventRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderReturnRepository orderReturnRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OrderService orderService;

    @Autowired
    private StockReceivingService stockReceivingService;

    @Autowired
    private InventoryService inventoryService;

    private MockMvc mockMvc;
    private Product hoodie;
    private ProductVariant hoodieXl;
    private ProductVariant hoodieM;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        orderReturnRepository.deleteAll();
        orderRepository.deleteAll();
        stockMovementRepository.deleteAll();
        inventoryRepository.deleteAll();
        jdbcTemplate.execute("TRUNCATE product_catalog_events");
        productRepository.deleteAll();
        userRepository.deleteAll();

        userRepository.saveAndFlush(User.create(
                UUID.randomUUID(),
                OWNER_EMAIL,
                BCRYPT_HASH,
                "Elin Sandberg",
                Role.OWNER
        ));

        hoodie = Product.create(
                UUID.randomUUID(),
                "Heavyweight Cotton Hoodie",
                "480 gsm brushed-back cotton hoodie with a double-layer hood.",
                "Apparel"
        );
        hoodieXl = hoodie.addVariant(
                UUID.randomUUID(),
                "HOOD-NVY-XL",
                "5060123456004",
                "Size",
                "XL",
                new BigDecimal("24.00"),
                new BigDecimal("58.00"),
                680,
                5
        );
        hoodieM = hoodie.addVariant(
                UUID.randomUUID(),
                "HOOD-NVY-M",
                "5060123456011",
                "Size",
                "M",
                new BigDecimal("24.00"),
                new BigDecimal("58.00"),
                640,
                5
        );
        productRepository.saveAndFlush(hoodie);

        inventoryRepository.save(InventoryLevel.initialize(UUID.randomUUID(), hoodieXl, 12));
        inventoryRepository.save(InventoryLevel.initialize(UUID.randomUUID(), hoodieM, 2));
        inventoryRepository.flush();
    }

    @Test
    @WithMockUser(username = OWNER_EMAIL, roles = "OWNER")
    @DisplayName("Retiring a product hides it from the working catalogue but keeps it visible on request")
    void retiringProductHidesItFromWorkingCatalogue() throws Exception {
        mockMvc.perform(post("/api/v1/products/{id}/retire", hoodie.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.variants.length()").value(2))
                .andExpect(jsonPath("$.data.variants[0].active").value(true))
                .andExpect(jsonPath("$.data.variants[1].active").value(true));

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(get("/api/v1/products").param("includeRetired", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(hoodie.getId().toString()))
                .andExpect(jsonPath("$.data[0].active").value(false));

        mockMvc.perform(get("/api/v1/products/{id}", hoodie.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.variants.length()").value(2));
    }

    @Test
    @WithMockUser(username = OWNER_EMAIL, roles = "OWNER")
    @DisplayName("Retiring one variant leaves its sibling sellable and visible")
    void retiringOneVariantLeavesItsSiblingSellable() throws Exception {
        mockMvc.perform(post(
                        "/api/v1/products/{productId}/variants/{variantId}/retire",
                        hoodie.getId(),
                        hoodieXl.getId()
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.variants.length()").value(2))
                .andExpect(jsonPath("$.data.variants[0].active").value(false))
                .andExpect(jsonPath("$.data.variants[1].active").value(true));

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].variants.length()").value(1))
                .andExpect(jsonPath("$.data[0].variants[0].sku").value("HOOD-NVY-M"));

        mockMvc.perform(get("/api/v1/products").param("includeRetired", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].variants.length()").value(2))
                .andExpect(jsonPath("$.data[0].variants[0].active").value(false))
                .andExpect(jsonPath("$.data[0].variants[1].active").value(true));
    }

    @Test
    @WithMockUser(username = OWNER_EMAIL, roles = "OWNER")
    @DisplayName("Retiring and restoring are reversible for both a product and a variant")
    void retiringAndRestoringAreReversible() throws Exception {
        mockMvc.perform(post("/api/v1/products/{id}/retire", hoodie.getId()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/products/{id}/restore", hoodie.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true));

        mockMvc.perform(post(
                        "/api/v1/products/{productId}/variants/{variantId}/retire",
                        hoodie.getId(),
                        hoodieXl.getId()
                ))
                .andExpect(status().isOk());
        mockMvc.perform(post(
                        "/api/v1/products/{productId}/variants/{variantId}/restore",
                        hoodie.getId(),
                        hoodieXl.getId()
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.variants[0].active").value(true));

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].variants.length()").value(2));
    }

    @Test
    @WithMockUser(username = "nadia.keller@northwind-supply.co", roles = "STAFF")
    @DisplayName("Staff cannot retire or restore products or variants")
    void staffCannotRetireOrRestore() throws Exception {
        mockMvc.perform(post("/api/v1/products/{id}/retire", hoodie.getId()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/products/{id}/restore", hoodie.getId()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(
                        "/api/v1/products/{productId}/variants/{variantId}/retire",
                        hoodie.getId(),
                        hoodieXl.getId()
                ))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(
                        "/api/v1/products/{productId}/variants/{variantId}/restore",
                        hoodie.getId(),
                        hoodieXl.getId()
                ))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = OWNER_EMAIL, roles = "OWNER")
    @DisplayName("Every retirement and restoration is recorded with catalogue snapshots")
    void retirementAndRestorationAreRecorded() throws Exception {
        mockMvc.perform(post("/api/v1/products/{id}/retire", hoodie.getId()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/products/{id}/restore", hoodie.getId()))
                .andExpect(status().isOk());
        mockMvc.perform(post(
                        "/api/v1/products/{productId}/variants/{variantId}/retire",
                        hoodie.getId(),
                        hoodieXl.getId()
                ))
                .andExpect(status().isOk());

        List<ProductCatalogEvent> events = productCatalogEventRepository.findAll();

        assertThat(events).hasSize(3);
        assertThat(events).anySatisfy(event -> {
            assertThat(event.getChangeType()).isEqualTo(ProductCatalogChangeType.RETIRED);
            assertThat(event.getScope()).isEqualTo(ProductCatalogScope.PRODUCT);
            assertThat(event.getProductId()).isEqualTo(hoodie.getId());
            assertThat(event.getVariantId()).isNull();
            assertThat(event.getProductName()).isEqualTo("Heavyweight Cotton Hoodie");
            assertThat(event.getVariantSku()).isNull();
            assertThat(event.getActorEmail()).isEqualTo(OWNER_EMAIL);
        });
        assertThat(events).anySatisfy(event -> {
            assertThat(event.getChangeType()).isEqualTo(ProductCatalogChangeType.RESTORED);
            assertThat(event.getScope()).isEqualTo(ProductCatalogScope.PRODUCT);
        });
        assertThat(events).anySatisfy(event -> {
            assertThat(event.getChangeType()).isEqualTo(ProductCatalogChangeType.RETIRED);
            assertThat(event.getScope()).isEqualTo(ProductCatalogScope.VARIANT);
            assertThat(event.getProductId()).isEqualTo(hoodie.getId());
            assertThat(event.getVariantId()).isEqualTo(hoodieXl.getId());
            assertThat(event.getVariantSku()).isEqualTo("HOOD-NVY-XL");
            assertThat(event.getProductName()).isEqualTo("Heavyweight Cotton Hoodie");
        });
    }

    @Test
    @WithMockUser(username = OWNER_EMAIL, roles = "OWNER")
    @DisplayName("Repeating a retirement does not append a duplicate history row")
    void repeatingRetirementDoesNotDuplicateHistory() throws Exception {
        mockMvc.perform(post("/api/v1/products/{id}/retire", hoodie.getId()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/products/{id}/retire", hoodie.getId()))
                .andExpect(status().isOk());

        assertThat(productCatalogEventRepository.findAll()).hasSize(1);
    }

    @Test
    @WithMockUser(username = OWNER_EMAIL, roles = "OWNER")
    @DisplayName("A retired variant cannot be ordered")
    void retiredVariantCannotBeOrdered() throws Exception {
        mockMvc.perform(post(
                        "/api/v1/products/{productId}/variants/{variantId}/retire",
                        hoodie.getId(),
                        hoodieXl.getId()
                ))
                .andExpect(status().isOk());

        assertThatThrownBy(() -> orderService.createManualOrder(orderFor("HOOD-NVY-XL")))
                .isInstanceOf(RetiredProductException.class)
                .hasMessageContaining("HOOD-NVY-XL")
                .hasMessageContaining("cannot be ordered");

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerName": "Amelia Hart",
                                  "customerEmail": "amelia.hart@example.com",
                                  "shippingAddress": "18 Bramble Lane, Bristol BS6 5QT, United Kingdom",
                                  "customerShippingCharge": 4.95,
                                  "items": [
                                    {
                                      "sku": "HOOD-NVY-XL",
                                      "quantity": 1,
                                      "unitPrice": 58.00
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_RETIRED"))
                .andExpect(jsonPath("$.detail", containsString("HOOD-NVY-XL")));
    }

    @Test
    @WithMockUser(username = OWNER_EMAIL, roles = "OWNER")
    @DisplayName("A retired product blocks orders even when its variant flag is still active")
    void retiredProductCannotBeOrdered() {
        hoodie.retire();
        productRepository.saveAndFlush(hoodie);

        assertThat(hoodieXl.isActive()).isTrue();
        assertThatThrownBy(() -> orderService.createManualOrder(orderFor("HOOD-NVY-XL")))
                .isInstanceOf(RetiredProductException.class)
                .hasMessageContaining("cannot be ordered");
    }

    @Test
    @WithMockUser(username = OWNER_EMAIL, roles = "OWNER")
    @DisplayName("A retired variant cannot be received")
    void retiredVariantCannotBeReceived() {
        hoodieXl.retire();
        productRepository.saveAndFlush(hoodie);

        assertThatThrownBy(() -> stockReceivingService.receive(
                hoodieXl.getId(),
                5,
                null,
                "Supplier delivery from the Manchester mill"
        ))
                .isInstanceOf(RetiredProductException.class)
                .hasMessageContaining("HOOD-NVY-XL")
                .hasMessageContaining("cannot be received");

        InventoryLevel level = inventoryRepository.findByProductVariantId(hoodieXl.getId()).orElseThrow();
        assertThat(level.getOnHandQuantity()).isEqualTo(12);
    }

    @Test
    @WithMockUser(username = OWNER_EMAIL, roles = "OWNER")
    @DisplayName("A retired product cannot be received against even when its variant flag is active")
    void retiredProductCannotBeReceived() {
        hoodie.retire();
        productRepository.saveAndFlush(hoodie);

        assertThatThrownBy(() -> stockReceivingService.receive(
                hoodieXl.getId(),
                5,
                null,
                "Supplier delivery from the Manchester mill"
        ))
                .isInstanceOf(RetiredProductException.class)
                .hasMessageContaining("cannot be received");

        InventoryLevel level = inventoryRepository.findByProductVariantId(hoodieXl.getId()).orElseThrow();
        assertThat(level.getOnHandQuantity()).isEqualTo(12);
    }

    @Test
    @WithMockUser(username = OWNER_EMAIL, roles = "OWNER")
    @DisplayName("Returns can still restock a retired variant")
    void returnsCanStillRestockARetiredVariant() {
        hoodieXl.retire();
        productRepository.saveAndFlush(hoodie);

        inventoryService.restockReturn(
                hoodieXl.getId(),
                2,
                UUID.randomUUID(),
                null,
                "Customer return inspected and returned to shelf"
        );

        InventoryLevel level = inventoryRepository.findByProductVariantId(hoodieXl.getId()).orElseThrow();
        assertThat(level.getOnHandQuantity()).isEqualTo(14);
        assertThat(stockMovementRepository.findAll())
                .anyMatch(movement -> movement.getReason() == StockMovementReason.RETURN_RESTOCK
                        && "HOOD-NVY-XL".equals(movement.getSku()));
    }

    @Test
    @WithMockUser(username = OWNER_EMAIL, roles = "OWNER")
    @DisplayName("Past order lines and stock movements remain visible after retirement")
    void pastHistoryRemainsVisibleAfterRetirement() {
        OrderResponse placed = orderService.createManualOrder(orderFor("HOOD-NVY-XL"));

        hoodieXl.retire();
        productRepository.saveAndFlush(hoodie);

        OrderResponse reread = orderService.getOrderById(placed.id());
        assertThat(reread.items()).hasSize(1);
        assertThat(reread.items().getFirst().sku()).isEqualTo("HOOD-NVY-XL");

        List<StockMovement> movements = stockMovementRepository.findAll();
        assertThat(movements).anyMatch(movement ->
                "HOOD-NVY-XL".equals(movement.getSku())
                        && "Heavyweight Cotton Hoodie".equals(movement.getProductName())
                        && movement.getReason() == StockMovementReason.ORDER_RESERVED
        );
    }

    @Test
    @WithMockUser(username = OWNER_EMAIL, roles = "OWNER")
    @DisplayName("Low-stock warnings ignore retired variants")
    void lowStockIgnoresRetiredVariants() throws Exception {
        mockMvc.perform(get("/api/v1/products/low-stock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].sku").value("HOOD-NVY-M"));

        mockMvc.perform(post(
                        "/api/v1/products/{productId}/variants/{variantId}/retire",
                        hoodie.getId(),
                        hoodieM.getId()
                ))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/products/low-stock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    private static CreateOrderRequest orderFor(String sku) {
        return new CreateOrderRequest(
                "Amelia Hart",
                "amelia.hart@example.com",
                "18 Bramble Lane, Bristol BS6 5QT, United Kingdom",
                new BigDecimal("4.95"),
                List.of(new CreateOrderItemRequest(sku, 1, new BigDecimal("58.00")))
        );
    }
}
