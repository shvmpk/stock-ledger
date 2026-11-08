package com.shvmpk.stock_ledger.integration.service;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import com.shvmpk.stock_ledger.inventory.entity.InventoryLevel;
import com.shvmpk.stock_ledger.inventory.repository.InventoryRepository;
import com.shvmpk.stock_ledger.order.dto.CsvImportResult;
import com.shvmpk.stock_ledger.order.entity.Order;
import com.shvmpk.stock_ledger.order.entity.OrderStatus;
import com.shvmpk.stock_ledger.order.repository.OrderRepository;
import com.shvmpk.stock_ledger.order.service.OrderCsvImportService;
import com.shvmpk.stock_ledger.product.entity.Product;
import com.shvmpk.stock_ledger.product.entity.ProductVariant;
import com.shvmpk.stock_ledger.product.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@DisplayName("Order CSV Streaming Import Integration Tests (Channel 2 Ingestion)")
class OrderCsvImportIntegrationTest {

    @Autowired
    private OrderCsvImportService csvImportService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private com.shvmpk.stock_ledger.order.repository.OrderReturnRepository orderReturnRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private com.shvmpk.stock_ledger.product.repository.ProductVariantRepository productVariantRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private com.shvmpk.stock_ledger.inventory.repository.StockMovementRepository stockMovementRepository;

    private ProductVariant hoodieVariant;

    @BeforeEach
    void setUp() {
        cleanDatabase();

        Product hoodie = Product.create(
                UUID.randomUUID(),
                "Heavyweight Cotton Hoodie",
                "450 GSM organic French terry cotton hoodie",
                "Apparel"
        );
        hoodieVariant = hoodie.addVariant(
                UUID.randomUUID(),
                "HOOD-NVY-CSV",
                "5060123456789",
                "Color / Size",
                "Navy / XL",
                new BigDecimal("28.00"),
                new BigDecimal("68.00"),
                750,
                5
        );
        productRepository.save(hoodie);

        InventoryLevel inventory = InventoryLevel.initialize(UUID.randomUUID(), hoodieVariant, 10);
        inventoryRepository.saveAndFlush(inventory);
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    @DisplayName("Should stream CSV, import valid rows atomically, and return structured partial-success report")
    void shouldStreamAndImportCsvWithPartialSuccess() {
        String csvContent = """
                order_number,customer_name,customer_email,shipping_address,customer_shipping_charge,sku,quantity,unit_price
                ORD-CSV-1001,Astrid Lindqvist,astrid@scandiboutique.se,"Kungsgatan 44, 111 35 Stockholm, Sweden",4.99,HOOD-NVY-CSV,2,68.00
                ORD-CSV-1002,Lars Larsson,lars@nordicwear.se,"Stortorget 12, 211 22 Malmo, Sweden",5.50,NONEXISTENT-SKU,1,25.00
                ORD-CSV-1003,Elin Sundqvist,elin@goteborg.se,"Avenyn 5, 411 36 Gothenburg, Sweden",0.00,HOOD-NVY-CSV,999,68.00
                ORD-CSV-1004,Erik Johansson,erik@scanditech.se,"Drottninggatan 10, 111 51 Stockholm, Sweden",4.99,HOOD-NVY-CSV,1,68.00
                """;

        ByteArrayInputStream inputStream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));
        CsvImportResult result = csvImportService.importOrdersFromCsv(inputStream);

        assertThat(result.totalRows()).isEqualTo(4);
        assertThat(result.importedOrders()).isEqualTo(3);
        assertThat(result.waitingForStockOrders()).isEqualTo(1);
        assertThat(result.failedRows()).isEqualTo(1);
        assertThat(result.errors()).hasSize(1);

        assertThat(result.errors()).anyMatch(err ->
                err.rowNumber() == 2 && err.sku().equals("NONEXISTENT-SKU") && err.error().contains("not found")
        );

        Order order1 = orderRepository.findByOrderNumber("ORD-CSV-1001").orElseThrow();
        assertThat(order1.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order1.getItems().getFirst().getReservedQuantity()).isEqualTo(2);

        Order order3 = orderRepository.findByOrderNumber("ORD-CSV-1003").orElseThrow();
        assertThat(order3.getStatus()).isEqualTo(OrderStatus.WAITING_FOR_STOCK);
        assertThat(order3.getItems().getFirst().getReservedQuantity()).isZero();

        Order order4 = orderRepository.findByOrderNumber("ORD-CSV-1004").orElseThrow();
        assertThat(order4.getStatus()).isEqualTo(OrderStatus.PENDING);

        // Rows 1 and 4 reserve 2 + 1 = 3 units; the 999-unit row waits for stock.
        InventoryLevel inventory = inventoryRepository.findByProductVariantId(hoodieVariant.getId()).orElseThrow();
        assertThat(inventory.getOnHandQuantity()).isEqualTo(10);
        assertThat(inventory.getReservedQuantity()).isEqualTo(3);
        assertThat(inventory.getAvailableQuantity()).isEqualTo(7);
    }

    @Test
    @DisplayName("Should reject a CSV row whose SKU has been retired and leave the order unimported")
    void shouldRejectRetiredSku() {
        hoodieVariant.retire();
        productRepository.saveAndFlush(hoodieVariant.getProduct());

        String csvContent = """
                order_number,customer_name,customer_email,shipping_address,customer_shipping_charge,sku,quantity,unit_price
                ORD-CSV-2001,Ingrid Nilsen,ingrid@fjordhome.no,"Karl Johans gate 22, 0159 Oslo, Norway",4.99,HOOD-NVY-CSV,1,68.00
                """;

        CsvImportResult result = csvImportService.importOrdersFromCsv(
                new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8))
        );

        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.importedOrders()).isZero();
        assertThat(result.failedRows()).isEqualTo(1);
        assertThat(result.errors()).singleElement().satisfies(error -> {
            assertThat(error.rowNumber()).isEqualTo(1);
            assertThat(error.sku()).isEqualTo("HOOD-NVY-CSV");
            assertThat(error.error()).contains("retired").contains("cannot be ordered");
        });
        assertThat(orderRepository.count()).isZero();
    }

    private void cleanDatabase() {
        orderReturnRepository.deleteAll();
        orderRepository.deleteAll();
        stockMovementRepository.deleteAll();
        inventoryRepository.deleteAll();
        productVariantRepository.deleteAll();
        productRepository.deleteAll();
    }
}
