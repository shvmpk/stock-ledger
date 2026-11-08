package com.shvmpk.stock_ledger.integration.service;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import com.shvmpk.stock_ledger.common.error.InsufficientStockException;
import com.shvmpk.stock_ledger.inventory.entity.InventoryLevel;
import com.shvmpk.stock_ledger.inventory.entity.StockMovement;
import com.shvmpk.stock_ledger.inventory.entity.StockMovementReason;
import com.shvmpk.stock_ledger.inventory.repository.InventoryRepository;
import com.shvmpk.stock_ledger.inventory.repository.StockMovementRepository;
import com.shvmpk.stock_ledger.order.dto.CreateOrderItemRequest;
import com.shvmpk.stock_ledger.order.dto.CreateOrderRequest;
import com.shvmpk.stock_ledger.order.dto.OrderResponse;
import com.shvmpk.stock_ledger.order.entity.Order;
import com.shvmpk.stock_ledger.order.entity.OrderStatus;
import com.shvmpk.stock_ledger.order.repository.OrderRepository;
import com.shvmpk.stock_ledger.order.service.OrderService;
import com.shvmpk.stock_ledger.product.entity.Product;
import com.shvmpk.stock_ledger.product.entity.ProductVariant;
import com.shvmpk.stock_ledger.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
@DisplayName("OrderService Integration Tests (Manual Order & Atomic Stock Reservation)")
class OrderServiceIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private com.shvmpk.stock_ledger.order.repository.OrderReturnRepository orderReturnRepository;

    @Autowired
    private com.shvmpk.stock_ledger.product.repository.ProductVariantRepository productVariantRepository;

    private ProductVariant hoodieVariant;

    @BeforeEach
    void setUp() {
        orderReturnRepository.deleteAll();
        orderRepository.deleteAll();
        stockMovementRepository.deleteAll();
        inventoryRepository.deleteAll();
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
                "HOOD-NVY-SVC",
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
        inventoryRepository.save(inventory);
    }

    @Test
    @DisplayName("Should successfully create order, persist order items, and reserve inventory atomically")
    void shouldCreateOrderAndReserveInventory() {
        CreateOrderRequest request = new CreateOrderRequest(
                "Astrid Lindqvist",
                "astrid@scandiboutique.se",
                "Kungsgatan 44, 111 35 Stockholm, Sweden",
                new BigDecimal("4.99"),
                List.of(new CreateOrderItemRequest("HOOD-NVY-SVC", 3, new BigDecimal("68.00")))
        );

        OrderResponse response = orderService.createManualOrder(request);

        assertThat(response).isNotNull();
        assertThat(response.orderNumber()).startsWith("ORD-");
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().sku()).isEqualTo("HOOD-NVY-SVC");
        assertThat(response.items().getFirst().quantity()).isEqualTo(3);

        Order persistedOrder = orderRepository.findById(response.id()).orElseThrow();
        assertThat(persistedOrder.getCustomerName()).isEqualTo("Astrid Lindqvist");
        assertThat(persistedOrder.getItems()).hasSize(1);

        InventoryLevel updatedInventory = inventoryRepository.findByProductVariantId(hoodieVariant.getId()).orElseThrow();
        assertThat(updatedInventory.getOnHandQuantity()).isEqualTo(10);
        assertThat(updatedInventory.getReservedQuantity()).isEqualTo(3);
        assertThat(updatedInventory.getAvailableQuantity()).isEqualTo(7);

        List<StockMovement> movements = stockMovementRepository.findAll();
        assertThat(movements).anyMatch(m ->
                m.getReason() == StockMovementReason.ORDER_RESERVED
                        && m.getDeltaReserved() == 3
                        && m.getDeltaOnHand() == 0
        );
    }

    @Test
    @DisplayName("Should reject order and rollback when available stock is insufficient")
    void shouldRejectOrderWhenStockInsufficient() {
        CreateOrderRequest request = new CreateOrderRequest(
                "Astrid Lindqvist",
                "astrid@scandiboutique.se",
                "Kungsgatan 44, 111 35 Stockholm, Sweden",
                new BigDecimal("4.99"),
                List.of(new CreateOrderItemRequest("HOOD-NVY-SVC", 15, new BigDecimal("68.00")))
        );

        assertThatThrownBy(() -> orderService.createManualOrder(request))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining(
                        "You asked for 15 units of SKU 'HOOD-NVY-SVC'. 10 are on the shelf and 0 are reserved "
                                + "for open orders, leaving 10 available. Receive more stock, or reduce this order."
                );

        InventoryLevel unchangedInventory = inventoryRepository.findByProductVariantId(hoodieVariant.getId()).orElseThrow();
        assertThat(unchangedInventory.getReservedQuantity()).isEqualTo(0);
        assertThat(unchangedInventory.getAvailableQuantity()).isEqualTo(10);
    }
}
