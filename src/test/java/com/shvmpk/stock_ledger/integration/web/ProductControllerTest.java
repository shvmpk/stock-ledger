package com.shvmpk.stock_ledger.integration.web;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import com.shvmpk.stock_ledger.inventory.entity.InventoryLevel;
import com.shvmpk.stock_ledger.inventory.repository.InventoryRepository;
import com.shvmpk.stock_ledger.product.entity.Product;
import com.shvmpk.stock_ledger.product.entity.ProductVariant;
import com.shvmpk.stock_ledger.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shvmpk.stock_ledger.inventory.repository.StockMovementRepository;
import com.shvmpk.stock_ledger.order.repository.OrderRepository;
import com.shvmpk.stock_ledger.order.repository.OrderReturnRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
@DisplayName("ProductController Integration Tests (Task 3)")
class ProductControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private OrderReturnRepository orderReturnRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        orderReturnRepository.deleteAll();
        orderRepository.deleteAll();
        stockMovementRepository.deleteAll();
        inventoryRepository.deleteAll();
        productRepository.deleteAll();

        // 1. High-stock product
        Product hoodie = Product.create(UUID.randomUUID(), "Heavyweight Zip Hoodie", "Organic cotton", "Apparel");
        ProductVariant vNormal = hoodie.addVariant(
                UUID.randomUUID(),
                "HOOD-ZIP-BLK-L",
                "5060123999001",
                "Size / Color",
                "L / Black",
                new BigDecimal("25.00"),
                new BigDecimal("70.00"),
                600,
                5
        );
        productRepository.save(hoodie);
        inventoryRepository.save(InventoryLevel.initialize(UUID.randomUUID(), vNormal, 20));

        // 2. Low-stock product (onHand = 4, reserved = 2, available = 2 <= minStock 5)
        Product coffee = Product.create(UUID.randomUUID(), "Ethiopia Guji Washed", "Specialty coffee", "Coffee");
        ProductVariant vLow = coffee.addVariant(
                UUID.randomUUID(),
                "COF-GUJ-250G",
                "5060123999002",
                "Weight",
                "250g",
                new BigDecimal("7.00"),
                new BigDecimal("16.50"),
                260,
                5
        );
        productRepository.save(coffee);
        InventoryLevel invLow = InventoryLevel.initialize(UUID.randomUUID(), vLow, 4);
        inventoryRepository.save(invLow);
    }

    @Test
    @DisplayName("Unauthenticated request to /api/v1/products returns 401")
    void unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("STAFF can fetch all products with variant inventory levels")
    void staffCanFetchProductsWithStock() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].variants").isArray())
                .andExpect(jsonPath("$.data[0].variants[0].sku").exists())
                .andExpect(jsonPath("$.data[0].variants[0].available").isNumber());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("STAFF product responses contain no cost or margin value")
    void staffProductResponseWithholdsCostAndMargin() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].variants[*].costPrice").doesNotExist())
                .andExpect(jsonPath("$.data[*].variants[*].margin").doesNotExist())
                .andExpect(jsonPath("$.data[*].variants[*].sellingPrice", everyItem(notNullValue())));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("OWNER product responses keep cost and selling price")
    void ownerProductResponseIncludesCostAndSellingPrice() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].variants[*].costPrice", everyItem(notNullValue())))
                .andExpect(jsonPath("$.data[*].variants[*].sellingPrice", everyItem(notNullValue())));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("STAFF can fetch low-stock variants")
    void staffCanFetchLowStockVariants() throws Exception {
        mockMvc.perform(get("/api/v1/products/low-stock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].sku").value("COF-GUJ-250G"))
                .andExpect(jsonPath("$.data[0].available").value(4))
                .andExpect(jsonPath("$.data[0].minStockThreshold").value(5))
                .andExpect(jsonPath("$.data[0].costPrice").doesNotExist())
                .andExpect(jsonPath("$.data[0].margin").doesNotExist());
    }
}
