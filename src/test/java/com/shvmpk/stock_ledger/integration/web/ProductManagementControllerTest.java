package com.shvmpk.stock_ledger.integration.web;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import com.shvmpk.stock_ledger.inventory.entity.InventoryLevel;
import com.shvmpk.stock_ledger.inventory.entity.StockMovement;
import com.shvmpk.stock_ledger.inventory.entity.StockMovementReason;
import com.shvmpk.stock_ledger.inventory.repository.InventoryRepository;
import com.shvmpk.stock_ledger.inventory.repository.StockMovementRepository;
import com.shvmpk.stock_ledger.inventory.service.StockReceivingService;
import com.shvmpk.stock_ledger.order.dto.CreateOrderItemRequest;
import com.shvmpk.stock_ledger.order.dto.CreateOrderRequest;
import com.shvmpk.stock_ledger.order.dto.OrderResponse;
import com.shvmpk.stock_ledger.order.dto.ShipOrderRequest;
import com.shvmpk.stock_ledger.order.entity.Order;
import com.shvmpk.stock_ledger.order.entity.OrderItem;
import com.shvmpk.stock_ledger.order.entity.OrderSource;
import com.shvmpk.stock_ledger.order.entity.OrderStatus;
import com.shvmpk.stock_ledger.order.repository.OrderRepository;
import com.shvmpk.stock_ledger.order.repository.OrderReturnRepository;
import com.shvmpk.stock_ledger.order.service.OrderService;
import com.shvmpk.stock_ledger.product.entity.Product;
import com.shvmpk.stock_ledger.product.entity.ProductVariant;
import com.shvmpk.stock_ledger.product.repository.ProductRepository;
import com.shvmpk.stock_ledger.product.repository.ProductVariantRepository;
import com.shvmpk.stock_ledger.shipping.entity.Carrier;
import com.shvmpk.stock_ledger.shipping.entity.ParcelSize;
import com.shvmpk.stock_ledger.shipping.entity.ShippingPriceRate;
import com.shvmpk.stock_ledger.shipping.repository.ShippingPriceListRepository;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
@DisplayName("Product creation and editing")
class ProductManagementControllerTest {

    private static final String CREATE_PRODUCT = """
            {
              "name": "Merino Wool Crew Socks",
              "category": "Accessories",
              "description": "Fine-gauge merino knit, reinforced heel and toe.",
              "variants": [
                {
                  "sku": "SOCK-MRN-M",
                  "barcode": "5060123456718",
                  "optionName": "Size",
                  "optionValue": "M",
                  "costPrice": 4.50,
                  "sellingPrice": 14.00,
                  "weightGrams": 80,
                  "minStockThreshold": 10
                },
                {
                  "sku": "SOCK-MRN-L",
                  "barcode": "5060123456725",
                  "optionName": "Size",
                  "optionValue": "L",
                  "costPrice": 4.50,
                  "sellingPrice": 14.00,
                  "weightGrams": 85,
                  "minStockThreshold": 10
                },
                {
                  "sku": "SOCK-MRN-XL",
                  "barcode": "5060123456732",
                  "optionName": "Size",
                  "optionValue": "XL",
                  "costPrice": 4.80,
                  "sellingPrice": 14.00,
                  "weightGrams": 90,
                  "minStockThreshold": 6
                }
              ]
            }
            """;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderReturnRepository orderReturnRepository;

    @Autowired
    private StockReceivingService stockReceivingService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ShippingPriceListRepository shippingPriceListRepository;

    private MockMvc mockMvc;
    private Product hoodie;
    private ProductVariant hoodieVariant;

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
        shippingPriceListRepository.deleteAll();

        hoodie = Product.create(
                UUID.randomUUID(),
                "Heavyweight Cotton Hoodie",
                "480 gsm brushed-back cotton hoodie with a double-layer hood.",
                "Apparel"
        );
        hoodieVariant = hoodie.addVariant(
                UUID.randomUUID(),
                "HOOD-NVY-XL",
                "5060123456004",
                "Size / Color",
                "XL / Navy",
                new BigDecimal("24.00"),
                new BigDecimal("58.00"),
                680,
                5
        );
        productRepository.save(hoodie);
        inventoryRepository.save(InventoryLevel.initialize(UUID.randomUUID(), hoodieVariant, 12));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("An owner adds a three-variant product and every variant gets a zero-stock ledger row")
    void ownerCreatesProductWithThreeVariantsAndZeroStock() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_PRODUCT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Merino Wool Crew Socks"))
                .andExpect(jsonPath("$.data.category").value("Accessories"))
                .andExpect(jsonPath("$.data.variants.length()").value(3))
                .andExpect(jsonPath("$.data.variants[0].sku").value("SOCK-MRN-M"))
                .andExpect(jsonPath("$.data.variants[0].weightGrams").value(80))
                .andExpect(jsonPath("$.data.variants[0].costPrice").value(4.50))
                .andExpect(jsonPath("$.data.variants[0].onHand").value(0))
                .andExpect(jsonPath("$.data.variants[0].reserved").value(0))
                .andExpect(jsonPath("$.data.variants[0].available").value(0));

        Product created = productRepository.findByName("Merino Wool Crew Socks").orElseThrow();
        assertThat(created.getVariants()).hasSize(3);
        for (ProductVariant variant : created.getVariants()) {
            InventoryLevel level = inventoryRepository.findByProductVariantId(variant.getId()).orElseThrow();
            assertThat(level.getOnHandQuantity()).isZero();
            assertThat(level.getReservedQuantity()).isZero();
        }
        assertThat(stockMovementRepository.count()).isZero();
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("An owner saves a variant without cost, weight or reorder level and reads them back as unknown")
    void ownerCreatesVariantWithUnknownOptionalFields() throws Exception {
        String request = """
                {
                  "name": "Linen Sample Swatch Set",
                  "category": "Homeware",
                  "description": "Three natural linen swatches for colour matching.",
                  "variants": [
                    {
                      "sku": "SWATCH-LIN-01",
                      "optionName": "Fabric",
                      "optionValue": "Natural linen",
                      "sellingPrice": 14.00
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.variants[0].costPrice").doesNotExist())
                .andExpect(jsonPath("$.data.variants[0].weightGrams").value(nullValue()))
                .andExpect(jsonPath("$.data.variants[0].minStockThreshold").value(nullValue()))
                .andExpect(jsonPath("$.data.variants[0].isLowStock").value(false));

        Product created = productRepository.findByName("Linen Sample Swatch Set").orElseThrow();
        ProductVariant variant = created.getVariants().getFirst();
        assertThat(variant.getCostPrice()).isNull();
        assertThat(variant.getWeightGrams()).isNull();
        assertThat(variant.getMinStockThreshold()).isNull();
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("An explicit zero cost, weight and reorder level stay real zeros")
    void ownerCreatesVariantWithExplicitZeros() throws Exception {
        String request = """
                {
                  "name": "Linen Sample Swatch Set",
                  "category": "Homeware",
                  "variants": [
                    {
                      "sku": "SWATCH-LIN-01",
                      "optionName": "Fabric",
                      "optionValue": "Natural linen",
                      "costPrice": 0.00,
                      "sellingPrice": 14.00,
                      "weightGrams": 0,
                      "minStockThreshold": 0
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.variants[0].costPrice").value(0.00))
                .andExpect(jsonPath("$.data.variants[0].weightGrams").value(0))
                .andExpect(jsonPath("$.data.variants[0].minStockThreshold").value(0));

        Product created = productRepository.findByName("Linen Sample Swatch Set").orElseThrow();
        ProductVariant variant = created.getVariants().getFirst();
        assertThat(variant.getCostPrice()).isEqualByComparingTo("0.00");
        assertThat(variant.getWeightGrams()).isZero();
        assertThat(variant.getMinStockThreshold()).isZero();
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("A variant without a reorder level is left out of the low-stock list")
    void variantWithoutReorderLevelIsNotLowStock() throws Exception {
        String request = """
                {
                  "name": "Linen Sample Swatch Set",
                  "category": "Homeware",
                  "variants": [
                    {
                      "sku": "SWATCH-LIN-01",
                      "optionName": "Fabric",
                      "optionValue": "Natural linen",
                      "sellingPrice": 14.00
                    },
                    {
                      "sku": "SWATCH-COT-01",
                      "optionName": "Fabric",
                      "optionValue": "Cotton canvas",
                      "sellingPrice": 12.00,
                      "minStockThreshold": 4
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/products/low-stock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].sku", containsInAnyOrder("SWATCH-COT-01")))
                .andExpect(jsonPath("$.data[*].minStockThreshold", containsInAnyOrder(4)));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("STAFF cannot create a product")
    void staffCannotCreateProduct() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_PRODUCT))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("An unauthenticated request cannot create a product")
    void anonymousCannotCreateProduct() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_PRODUCT))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("A duplicate SKU inside the request is refused with a field-level message")
    void duplicateSkuInsideRequestIsRefused() throws Exception {
        String body = CREATE_PRODUCT.replace("\"SOCK-MRN-L\"", "\"sock-mrn-m\"");

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.invalidParams[0].field").value("variants[1].sku"))
                .andExpect(jsonPath("$.invalidParams[0].message", containsString("SOCK-MRN-M")));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("A SKU already in the catalogue is refused with a field-level message naming it")
    void duplicateSkuAgainstCatalogueIsRefused() throws Exception {
        String body = CREATE_PRODUCT.replace("\"SOCK-MRN-M\"", "\"hood-nvy-xl\"");

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.invalidParams[0].field").value("variants[0].sku"))
                .andExpect(jsonPath("$.invalidParams[0].message", containsString("HOOD-NVY-XL")));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("Negative prices, weights and thresholds are refused per field")
    void negativeValuesAreRefusedPerField() throws Exception {
        String body = """
                {
                  "name": "Merino Wool Crew Socks",
                  "variants": [
                    {
                      "sku": "SOCK-MRN-M",
                      "costPrice": -4.50,
                      "sellingPrice": -14.00,
                      "weightGrams": -80,
                      "minStockThreshold": -10
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.invalidParams[*].field", containsInAnyOrder(
                        "variants[0].costPrice",
                        "variants[0].sellingPrice",
                        "variants[0].weightGrams",
                        "variants[0].minStockThreshold"
                )));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("A blank name and an empty variant list are refused per field")
    void blankNameAndEmptyVariantsAreRefused() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "   ", "variants": []}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.invalidParams[*].field", containsInAnyOrder("name", "variants")));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("An owner reads one product with cost and weight")
    void ownerReadsProductByIdWithCost() throws Exception {
        mockMvc.perform(get("/api/v1/products/{id}", hoodie.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Heavyweight Cotton Hoodie"))
                .andExpect(jsonPath("$.data.variants[0].costPrice").value(24.00))
                .andExpect(jsonPath("$.data.variants[0].sellingPrice").value(58.00))
                .andExpect(jsonPath("$.data.variants[0].weightGrams").value(680));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("STAFF reads one product without cost but with weight")
    void staffReadsProductByIdWithoutCost() throws Exception {
        mockMvc.perform(get("/api/v1/products/{id}", hoodie.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.variants[0].costPrice").doesNotExist())
                .andExpect(jsonPath("$.data.variants[0].sellingPrice").value(58.00))
                .andExpect(jsonPath("$.data.variants[0].weightGrams").value(680));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("An owner edits a product, changes a variant and adds another")
    void ownerEditsProductAndAddsVariant() throws Exception {
        String body = """
                {
                  "name": "Heavyweight Cotton Hoodie 2026",
                  "category": "Apparel",
                  "description": "Reissued with a heavier 500 gsm loopback fleece.",
                  "variants": [
                    {
                      "id": "%s",
                      "sku": "HOOD-NVY-XL-26",
                      "barcode": "5060123456004",
                      "optionName": "Size / Color",
                      "optionValue": "XL / Navy",
                      "costPrice": 26.00,
                      "sellingPrice": 62.00,
                      "weightGrams": 720,
                      "minStockThreshold": 6
                    },
                    {
                      "sku": "HOOD-NVY-XXL",
                      "barcode": "5060123456011",
                      "optionName": "Size / Color",
                      "optionValue": "XXL / Navy",
                      "costPrice": 26.00,
                      "sellingPrice": 62.00,
                      "weightGrams": 740,
                      "minStockThreshold": 4
                    }
                  ]
                }
                """.formatted(hoodieVariant.getId());

        mockMvc.perform(put("/api/v1/products/{id}", hoodie.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Heavyweight Cotton Hoodie 2026"))
                .andExpect(jsonPath("$.data.variants.length()").value(2))
                .andExpect(jsonPath("$.data.variants[0].sku").value("HOOD-NVY-XL-26"))
                .andExpect(jsonPath("$.data.variants[0].weightGrams").value(720))
                .andExpect(jsonPath("$.data.variants[1].sku").value("HOOD-NVY-XXL"))
                .andExpect(jsonPath("$.data.variants[1].onHand").value(0));

        Product updated = productRepository.findById(hoodie.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Heavyweight Cotton Hoodie 2026");
        assertThat(updated.getVariants()).hasSize(2);

        ProductVariant renamed = productVariantRepository.findBySkuIgnoreCase("HOOD-NVY-XL-26").orElseThrow();
        assertThat(renamed.getSellingPrice()).isEqualByComparingTo("62.00");
        assertThat(renamed.getWeightGrams()).isEqualTo(720);

        ProductVariant added = productVariantRepository.findBySkuIgnoreCase("HOOD-NVY-XXL").orElseThrow();
        InventoryLevel addedLevel = inventoryRepository.findByProductVariantId(added.getId()).orElseThrow();
        assertThat(addedLevel.getOnHandQuantity()).isZero();
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("Editing a variant leaves past order lines and stock movements untouched")
    void editingDoesNotRewriteOrderLinesOrStockMovements() throws Exception {
        Order order = Order.create(
                "ORD-2026-0501",
                OrderSource.MANUAL,
                null,
                "Amelia Hart",
                "amelia.hart@example.com",
                "18 Bramble Lane, Bristol BS6 5QT, United Kingdom",
                new BigDecimal("4.95")
        );
        OrderItem item = OrderItem.create(order, hoodieVariant, 2, new BigDecimal("58.00"));
        order.addItem(item);
        orderRepository.save(order);

        StockMovement movement = StockMovement.create(
                hoodieVariant,
                hoodieVariant.getSku(),
                hoodieVariant.getProduct().getName(),
                12,
                0,
                StockMovementReason.RECEIVE,
                "RECEIVING",
                UUID.randomUUID(),
                null,
                "Initial stock receipt from supplier"
        );
        stockMovementRepository.save(movement);

        String body = """
                {
                  "name": "Heavyweight Cotton Hoodie 2026",
                  "category": "Apparel",
                  "variants": [
                    {
                      "id": "%s",
                      "sku": "HOOD-NVY-XL-26",
                      "optionName": "Size / Color",
                      "optionValue": "XL / Navy",
                      "costPrice": 26.00,
                      "sellingPrice": 62.00,
                      "weightGrams": 720,
                      "minStockThreshold": 6
                    }
                  ]
                }
                """.formatted(hoodieVariant.getId());

        mockMvc.perform(put("/api/v1/products/{id}", hoodie.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/orders/{id}", order.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].sku").value("HOOD-NVY-XL"))
                .andExpect(jsonPath("$.data.items[0].unitPrice").value(58.00));

        mockMvc.perform(get("/api/v1/stock/movements").param("sku", "HOOD-NVY-XL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].sku").value("HOOD-NVY-XL"))
                .andExpect(jsonPath("$.data[0].productName").value("Heavyweight Cotton Hoodie"));

        mockMvc.perform(get("/api/v1/stock/movements").param("sku", "HOOD-NVY-XL-26"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("An edit cannot take a SKU that belongs to another product")
    void editRefusesSkuOwnedByAnotherVariant() throws Exception {
        Product socks = Product.create(
                UUID.randomUUID(),
                "Merino Wool Crew Socks",
                null,
                "Accessories"
        );
        socks.addVariant(
                UUID.randomUUID(),
                "SOCK-MRN-M",
                "5060123456718",
                "Size",
                "M",
                new BigDecimal("4.50"),
                new BigDecimal("14.00"),
                80,
                10
        );
        productRepository.save(socks);

        String body = """
                {
                  "name": "Heavyweight Cotton Hoodie",
                  "variants": [
                    {
                      "id": "%s",
                      "sku": "SOCK-MRN-M",
                      "costPrice": 24.00,
                      "sellingPrice": 58.00,
                      "weightGrams": 680,
                      "minStockThreshold": 5
                    }
                  ]
                }
                """.formatted(hoodieVariant.getId());

        mockMvc.perform(put("/api/v1/products/{id}", hoodie.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.invalidParams[0].field").value("variants[0].sku"))
                .andExpect(jsonPath("$.invalidParams[0].message", containsString("SOCK-MRN-M")));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("An edit cannot attach a variant that belongs to another product")
    void editRefusesVariantIdFromAnotherProduct() throws Exception {
        Product socks = Product.create(
                UUID.randomUUID(),
                "Merino Wool Crew Socks",
                null,
                "Accessories"
        );
        ProductVariant sockVariant = socks.addVariant(
                UUID.randomUUID(),
                "SOCK-MRN-M",
                "5060123456718",
                "Size",
                "M",
                new BigDecimal("4.50"),
                new BigDecimal("14.00"),
                80,
                10
        );
        productRepository.save(socks);

        String body = """
                {
                  "name": "Heavyweight Cotton Hoodie",
                  "variants": [
                    {
                      "id": "%s",
                      "sku": "HOOD-NVY-XL",
                      "costPrice": 24.00,
                      "sellingPrice": 58.00,
                      "weightGrams": 680,
                      "minStockThreshold": 5
                    }
                  ]
                }
                """.formatted(sockVariant.getId());

        mockMvc.perform(put("/api/v1/products/{id}", hoodie.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.invalidParams[0].field").value("variants[0].id"));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("Editing an unknown product returns 404")
    void editUnknownProductReturns404() throws Exception {
        mockMvc.perform(put("/api/v1/products/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Merino Wool Crew Socks",
                                  "variants": [
                                    {"sku": "SOCK-MRN-M", "sellingPrice": 14.00}
                                  ]
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("An edit keeps variants that the request does not mention")
    void editKeepsOmittedVariants() throws Exception {
        ProductVariant second = hoodie.addVariant(
                UUID.randomUUID(),
                "HOOD-NVY-M",
                "5060123456011",
                "Size / Color",
                "M / Navy",
                new BigDecimal("24.00"),
                new BigDecimal("58.00"),
                640,
                5
        );
        productRepository.save(hoodie);
        inventoryRepository.save(InventoryLevel.initialize(UUID.randomUUID(), second, 4));

        String body = """
                {
                  "name": "Heavyweight Cotton Hoodie",
                  "variants": [
                    {
                      "id": "%s",
                      "sku": "HOOD-NVY-XL",
                      "costPrice": 24.00,
                      "sellingPrice": 58.00,
                      "weightGrams": 680,
                      "minStockThreshold": 5
                    }
                  ]
                }
                """.formatted(hoodieVariant.getId());

        mockMvc.perform(put("/api/v1/products/{id}", hoodie.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.variants.length()").value(2));

        assertThat(productVariantRepository.findBySkuIgnoreCase("HOOD-NVY-M")).isPresent();
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("An owner adds a product, receives it, sells it, and ships it without editing seed data")
    void ownerCanReceiveSellAndShipANewlyCreatedProduct() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_PRODUCT))
                .andExpect(status().isCreated());

        ProductVariant socks = productVariantRepository.findBySkuIgnoreCase("SOCK-MRN-M").orElseThrow();
        stockReceivingService.receive(
                socks.getId(),
                10,
                null,
                "Initial delivery from the merino mill"
        );

        shippingPriceListRepository.save(ShippingPriceRate.create(
                UUID.randomUUID(),
                Carrier.DHL,
                ParcelSize.MEDIUM,
                new BigDecimal("6.50")
        ));

        OrderResponse placedOrder = orderService.createManualOrder(new CreateOrderRequest(
                "Amelia Hart",
                "amelia.hart@example.com",
                "18 Bramble Lane, Bristol BS6 5QT, United Kingdom",
                new BigDecimal("4.95"),
                List.of(new CreateOrderItemRequest("SOCK-MRN-M", 2, new BigDecimal("14.00")))
        ));
        orderService.packOrder(placedOrder.id());
        OrderResponse shippedOrder = orderService.shipOrder(
                placedOrder.id(),
                new ShipOrderRequest(Carrier.DHL, ParcelSize.MEDIUM, "1234567890")
        );

        assertThat(shippedOrder.status()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(shippedOrder.trackingNumber()).isEqualTo("1234567890");

        InventoryLevel level = inventoryRepository.findByProductVariantId(socks.getId()).orElseThrow();
        assertThat(level.getOnHandQuantity()).isEqualTo(8);
        assertThat(level.getReservedQuantity()).isZero();

        assertThat(stockMovementRepository.findAll()).anyMatch(movement ->
                movement.getReason() == StockMovementReason.ORDER_SHIPPED
                        && "SOCK-MRN-M".equals(movement.getSku())
                        && "Merino Wool Crew Socks".equals(movement.getProductName())
                        && movement.getDeltaOnHand() == -2
                        && movement.getDeltaReserved() == -2
        );
    }
}
