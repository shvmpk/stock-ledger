package com.shvmpk.stock_ledger.integration.service;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import com.shvmpk.stock_ledger.export.service.DataExportService;
import com.shvmpk.stock_ledger.inventory.entity.InventoryLevel;
import com.shvmpk.stock_ledger.inventory.entity.StockMovement;
import com.shvmpk.stock_ledger.inventory.entity.StockMovementReason;
import com.shvmpk.stock_ledger.inventory.repository.InventoryRepository;
import com.shvmpk.stock_ledger.inventory.repository.StockMovementRepository;
import com.shvmpk.stock_ledger.order.entity.Order;
import com.shvmpk.stock_ledger.order.entity.OrderItem;
import com.shvmpk.stock_ledger.order.entity.OrderSource;
import com.shvmpk.stock_ledger.order.repository.OrderRepository;
import com.shvmpk.stock_ledger.order.repository.OrderReturnRepository;
import com.shvmpk.stock_ledger.product.entity.Product;
import com.shvmpk.stock_ledger.product.entity.ProductVariant;
import com.shvmpk.stock_ledger.product.repository.ProductRepository;
import com.shvmpk.stock_ledger.product.repository.ProductVariantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
@DisplayName("Data Export Service Integration Tests (Task 6.2)")
class DataExportServiceIntegrationTest {

    @Autowired
    private DataExportService dataExportService;

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

    @BeforeEach
    void setUp() {
        orderReturnRepository.deleteAll();
        orderRepository.deleteAll();
        stockMovementRepository.deleteAll();
        inventoryRepository.deleteAll();
        productVariantRepository.deleteAll();
        productRepository.deleteAll();

        // High-fidelity Nordic product
        Product product = Product.create(
                UUID.randomUUID(),
                "Heavyweight Cotton Hoodie",
                "450 GSM organic loopback French terry",
                "Apparel"
        );
        ProductVariant variant = product.addVariant(
                UUID.randomUUID(),
                "HOOD-NVY-EXP",
                "5060123456799",
                "Size / Color",
                "XL / Navy",
                new BigDecimal("24.00"),
                new BigDecimal("68.00"),
                650,
                5
        );
        productRepository.save(product);

        InventoryLevel inventory = InventoryLevel.initialize(UUID.randomUUID(), variant, 50);
        inventoryRepository.save(inventory);

        // Movement record
        StockMovement movement = StockMovement.create(
                variant,
                variant.getSku(),
                variant.getProduct().getName(),
                50,
                0,
                StockMovementReason.RECEIVE,
                "AUDIT",
                UUID.randomUUID(),
                null,
                "Initial batch receiving"
        );
        stockMovementRepository.save(movement);

        // Order record
        Order order = Order.create(
                "ORD-EXP-101",
                OrderSource.MANUAL,
                null,
                "Freja Lindqvist",
                "freja.lindqvist@example.se",
                "Kungsgatan 14, Stockholm",
                new BigDecimal("12.50")
        );
        OrderItem item = OrderItem.create(order, variant, 1, new BigDecimal("68.00"));
        order.addItem(item);
        order.markShipped("DHL", "MEDIUM", "1234567890", "https://track.dhl.com/1234567890", new BigDecimal("9.50"));
        orderRepository.save(order);
    }

    @Test
    @DisplayName("Should export catalog CSV with authentic attributes")
    void shouldExportCatalogCsv() {
        String csv = dataExportService.exportCatalogCsv();

        assertThat(csv).contains("Product Name,SKU,Barcode,Option Name,Option Value,Wholesale Cost,Retail Price,Weight Grams,On Hand,Reserved,Available");
        assertThat(csv).contains("Heavyweight Cotton Hoodie");
        assertThat(csv).contains("HOOD-NVY-EXP");
        assertThat(csv).contains("5060123456799");
        assertThat(csv).contains("24.00");
        assertThat(csv).contains("68.00");
    }

    @Test
    @DisplayName("Should write blank cost and weight when the values are unknown")
    void shouldWriteBlanksForUnknownOptionalFields() {
        Product swatches = Product.create(
                UUID.randomUUID(),
                "Linen Sample Swatch Set",
                "Three natural linen swatches for colour matching.",
                "Homeware"
        );
        ProductVariant swatch = swatches.addVariant(
                UUID.randomUUID(),
                "SWATCH-LIN-01",
                null,
                "Fabric",
                "Natural linen",
                null,
                new BigDecimal("14.00"),
                null,
                null
        );
        productRepository.save(swatches);
        inventoryRepository.save(InventoryLevel.initialize(UUID.randomUUID(), swatch, 0));

        String csv = dataExportService.exportCatalogCsv();

        assertThat(csv).contains("Linen Sample Swatch Set,SWATCH-LIN-01,,Fabric,Natural linen,,14.00,,0,0,0");
    }

    @Test
    @DisplayName("Should export orders CSV with shipping and financial details")
    void shouldExportOrdersCsv() {
        String csv = dataExportService.exportOrdersCsv();

        assertThat(csv).contains("Order Number,Source,Status,Customer Name,Customer Email,Shipping Address,Carrier,Tracking Number,Shipping Charge,Actual Cost");
        assertThat(csv).contains("ORD-EXP-101");
        assertThat(csv).contains("SHIPPED");
        assertThat(csv).contains("Freja Lindqvist");
        assertThat(csv).contains("12.50");
        assertThat(csv).contains("9.50");
    }

    @Test
    @DisplayName("Should export stock movements audit trail CSV")
    void shouldExportStockHistoryCsv() {
        String csv = dataExportService.exportStockHistoryCsv();

        assertThat(csv).contains("Movement ID,Product Variant ID,Delta On Hand,Delta Reserved,Reason,Reference Type,Reference ID,Notes,Created At");
        assertThat(csv).contains("RECEIVE");
        assertThat(csv).contains("50");
        assertThat(csv).contains("Initial batch receiving");
    }
}
