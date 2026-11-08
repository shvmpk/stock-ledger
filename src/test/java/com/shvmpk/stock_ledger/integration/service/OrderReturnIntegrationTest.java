package com.shvmpk.stock_ledger.integration.service;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import com.shvmpk.stock_ledger.common.error.InvalidOrderStateTransitionException;
import com.shvmpk.stock_ledger.common.error.InvalidReturnQuantityException;
import com.shvmpk.stock_ledger.inventory.entity.InventoryLevel;
import com.shvmpk.stock_ledger.inventory.entity.StockMovement;
import com.shvmpk.stock_ledger.inventory.entity.StockMovementReason;
import com.shvmpk.stock_ledger.inventory.repository.InventoryRepository;
import com.shvmpk.stock_ledger.inventory.repository.StockMovementRepository;
import com.shvmpk.stock_ledger.order.dto.CreateOrderItemRequest;
import com.shvmpk.stock_ledger.order.dto.CreateOrderRequest;
import com.shvmpk.stock_ledger.order.dto.OrderResponse;
import com.shvmpk.stock_ledger.order.dto.ProcessReturnRequest;
import com.shvmpk.stock_ledger.order.dto.ReturnResponse;
import com.shvmpk.stock_ledger.order.dto.ShipOrderRequest;
import com.shvmpk.stock_ledger.order.entity.ItemCondition;
import com.shvmpk.stock_ledger.order.entity.OrderStatus;
import com.shvmpk.stock_ledger.order.repository.OrderRepository;
import com.shvmpk.stock_ledger.order.service.OrderReturnService;
import com.shvmpk.stock_ledger.order.service.OrderService;
import com.shvmpk.stock_ledger.product.entity.Product;
import com.shvmpk.stock_ledger.product.entity.ProductVariant;
import com.shvmpk.stock_ledger.product.repository.ProductRepository;
import com.shvmpk.stock_ledger.shipping.entity.Carrier;
import com.shvmpk.stock_ledger.shipping.entity.ParcelSize;
import com.shvmpk.stock_ledger.shipping.entity.ShippingPriceRate;
import com.shvmpk.stock_ledger.shipping.repository.ShippingPriceListRepository;
import com.shvmpk.stock_ledger.user.entity.Role;
import com.shvmpk.stock_ledger.user.entity.User;
import com.shvmpk.stock_ledger.user.repository.UserRepository;
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
@DisplayName("OrderReturnService Integration Tests (Grading, Restocking & Audit Logging)")
class OrderReturnIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderReturnService returnService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private ShippingPriceListRepository shippingPriceListRepository;

    @Autowired
    private com.shvmpk.stock_ledger.order.repository.OrderReturnRepository orderReturnRepository;

    @Autowired
    private com.shvmpk.stock_ledger.product.repository.ProductVariantRepository productVariantRepository;

    @Autowired
    private UserRepository userRepository;

    private ProductVariant hoodieVariant;
    private OrderResponse shippedOrder;
    private UUID orderItemId;
    private User returnsOperator;

    @BeforeEach
    void setUp() {
        orderReturnRepository.deleteAll();
        orderRepository.deleteAll();
        stockMovementRepository.deleteAll();
        inventoryRepository.deleteAll();
        productVariantRepository.deleteAll();
        productRepository.deleteAll();
        shippingPriceListRepository.deleteAll();

        returnsOperator = userRepository.saveAndFlush(User.create(
                UUID.randomUUID(),
                "freja.lindholm@nordicliving.se",
                "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy",
                "Freja Lindholm",
                Role.STAFF
        ));

        Product hoodie = Product.create(
                UUID.randomUUID(),
                "Heavyweight Cotton Hoodie",
                "450 GSM organic French terry cotton hoodie",
                "Apparel"
        );
        hoodieVariant = hoodie.addVariant(
                UUID.randomUUID(),
                "HOOD-NVY-RET",
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

        ShippingPriceRate rate = ShippingPriceRate.create(
                UUID.randomUUID(),
                Carrier.DHL,
                ParcelSize.MEDIUM,
                new BigDecimal("6.50")
        );
        shippingPriceListRepository.save(rate);

        // Place and ship order with 2 units
        CreateOrderRequest request = new CreateOrderRequest(
                "Astrid Lindqvist",
                "astrid@scandiboutique.se",
                "Kungsgatan 44, 111 35 Stockholm, Sweden",
                new BigDecimal("4.99"),
                List.of(new CreateOrderItemRequest("HOOD-NVY-RET", 2, new BigDecimal("68.00")))
        );
        OrderResponse placed = orderService.createManualOrder(request);
        orderItemId = placed.items().getFirst().id();
        orderService.packOrder(placed.id());
        shippedOrder = orderService.shipOrder(placed.id(), new ShipOrderRequest(Carrier.DHL, ParcelSize.MEDIUM, "1234567890"));
    }

    @Test
    @DisplayName("Should return SELLABLE item, restock on-hand inventory, and log RETURN_RESTOCK movement")
    void shouldReturnSellableItemAndRestock() {
        // Current on-hand is 8 after shipping 2
        ProcessReturnRequest returnRequest = new ProcessReturnRequest(
                "Size too large for customer",
                List.of(new ProcessReturnRequest.ReturnItemRequest(orderItemId, 1, ItemCondition.SELLABLE))
        );

        ReturnResponse response = returnService.processReturn(shippedOrder.id(), returnRequest, null);

        assertThat(response).isNotNull();
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().restocked()).isTrue();
        assertThat(response.items().getFirst().condition()).isEqualTo(ItemCondition.SELLABLE);

        // Status should be PARTIALLY_RETURNED since 1 of 2 units was returned
        var order = orderRepository.findById(shippedOrder.id()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PARTIALLY_RETURNED);

        // Inventory should increase from 8 to 9
        InventoryLevel inventory = inventoryRepository.findByProductVariantId(hoodieVariant.getId()).orElseThrow();
        assertThat(inventory.getOnHandQuantity()).isEqualTo(9);

        // Movement audit
        List<StockMovement> movements = stockMovementRepository.findAll();
        assertThat(movements).anyMatch(m ->
                m.getReason() == StockMovementReason.RETURN_RESTOCK
                        && m.getDeltaOnHand() == 1
                        && m.getDeltaReserved() == 0
        );
    }

    @Test
    @DisplayName("Should return DAMAGED item without restocking on-hand inventory and log RETURN_DAMAGED movement")
    void shouldReturnDamagedItemWithoutRestocking() {
        // Return 1 sellable and 1 damaged to fully return the order
        ProcessReturnRequest returnRequest = new ProcessReturnRequest(
                "Fabric torn upon receipt",
                List.of(
                        new ProcessReturnRequest.ReturnItemRequest(orderItemId, 1, ItemCondition.DAMAGED),
                        new ProcessReturnRequest.ReturnItemRequest(orderItemId, 1, ItemCondition.SELLABLE)
                )
        );

        ReturnResponse response = returnService.processReturn(shippedOrder.id(), returnRequest, null);

        assertThat(response.items()).hasSize(2);

        // All 2 units returned -> Order status should be RETURNED
        var order = orderRepository.findById(shippedOrder.id()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.RETURNED);

        // Inventory should only increase by 1 (the sellable one): from 8 to 9
        InventoryLevel inventory = inventoryRepository.findByProductVariantId(hoodieVariant.getId()).orElseThrow();
        assertThat(inventory.getOnHandQuantity()).isEqualTo(9);

        // Check audit movements: one RETURN_RESTOCK and one RETURN_DAMAGED
        List<StockMovement> movements = stockMovementRepository.findAll();
        assertThat(movements).anyMatch(m ->
                m.getReason() == StockMovementReason.RETURN_DAMAGED
                        && m.getDeltaOnHand() == 0
                        && m.getDeltaReserved() == 0
        );
        assertThat(movements).anyMatch(m ->
                m.getReason() == StockMovementReason.RETURN_RESTOCK
                        && m.getDeltaOnHand() == 1
        );
    }

    @Test
    @DisplayName("Should reject an over-return with INVALID_RETURN_QUANTITY and the remaining quantity")
    void shouldRejectOverReturnWithDomainError() {
        ProcessReturnRequest returnRequest = new ProcessReturnRequest(
                "Customer shipped back too many units",
                List.of(new ProcessReturnRequest.ReturnItemRequest(orderItemId, 3, ItemCondition.SELLABLE))
        );

        assertThatThrownBy(() -> returnService.processReturn(shippedOrder.id(), returnRequest, null))
                .isInstanceOf(InvalidReturnQuantityException.class)
                .hasMessageContaining("Cannot return 3 units of SKU 'HOOD-NVY-RET'. Only 2 remaining unreturned units are available.")
                .extracting(ex -> ((InvalidReturnQuantityException) ex).getErrorCode())
                .isEqualTo("INVALID_RETURN_QUANTITY");
    }

    @Test
    @DisplayName("Should reject return request if order is not SHIPPED or PARTIALLY_RETURNED")
    void shouldRejectReturnOnPendingOrder() {
        CreateOrderRequest request = new CreateOrderRequest(
                "Astrid Lindqvist",
                "astrid@scandiboutique.se",
                "Kungsgatan 44, 111 35 Stockholm, Sweden",
                new BigDecimal("4.99"),
                List.of(new CreateOrderItemRequest("HOOD-NVY-RET", 1, new BigDecimal("68.00")))
        );
        OrderResponse pendingOrder = orderService.createManualOrder(request);

        ProcessReturnRequest returnRequest = new ProcessReturnRequest(
                "Cancel before shipment",
                List.of(new ProcessReturnRequest.ReturnItemRequest(pendingOrder.items().getFirst().id(), 1, ItemCondition.SELLABLE))
        );

        assertThatThrownBy(() -> returnService.processReturn(pendingOrder.id(), returnRequest, null))
                .isInstanceOf(InvalidOrderStateTransitionException.class)
                .hasMessageContaining("Cannot transition order from status 'PENDING' to 'RETURNED'");
    }

    @Test
    @DisplayName("Should return an empty history for a shipped order without returns")
    void shouldReturnEmptyHistoryForOrderWithoutReturns() {
        assertThat(returnService.getReturnsForOrder(shippedOrder.id())).isEmpty();
    }

    @Test
    @DisplayName("Should reject return history for an unknown order")
    void shouldRejectHistoryForUnknownOrder() {
        UUID unknownOrderId = UUID.randomUUID();

        assertThatThrownBy(() -> returnService.getReturnsForOrder(unknownOrderId))
                .isInstanceOf(com.shvmpk.stock_ledger.common.error.ResourceNotFoundException.class)
                .hasMessageContaining("Order")
                .hasMessageContaining(unknownOrderId.toString());
    }

    @Test
    @DisplayName("Should return multiple returns with operator notes and processed-by identity")
    void shouldReturnHistoryWithOperatorNotesAndIdentity() {
        ProcessReturnRequest firstReturn = new ProcessReturnRequest(
                "Hoodie arrived with a loose seam",
                "Quality team inspected the seam and approved restocking.",
                List.of(new ProcessReturnRequest.ReturnItemRequest(orderItemId, 1, ItemCondition.SELLABLE))
        );
        ProcessReturnRequest secondReturn = new ProcessReturnRequest(
                "Second unit had a damaged cuff",
                "Damaged cuff photographed and written off by the warehouse team.",
                List.of(new ProcessReturnRequest.ReturnItemRequest(orderItemId, 1, ItemCondition.DAMAGED))
        );

        returnService.processReturn(shippedOrder.id(), firstReturn, returnsOperator.getId());
        returnService.processReturn(shippedOrder.id(), secondReturn, returnsOperator.getId());

        List<ReturnResponse> history = returnService.getReturnsForOrder(shippedOrder.id());

        assertThat(history).hasSize(2);
        assertThat(history)
                .extracting(ReturnResponse::operatorNotes)
                .containsExactlyInAnyOrder(
                        "Quality team inspected the seam and approved restocking.",
                        "Damaged cuff photographed and written off by the warehouse team."
                );
        assertThat(history).allSatisfy(returnResponse -> {
            assertThat(returnResponse.processedBy()).isNotNull();
            assertThat(returnResponse.processedBy().id()).isEqualTo(returnsOperator.getId());
            assertThat(returnResponse.processedBy().fullName()).isEqualTo("Freja Lindholm");
            assertThat(returnResponse.processedBy().email()).isEqualTo("freja.lindholm@nordicliving.se");
        });

        var order = orderRepository.findById(shippedOrder.id()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.RETURNED);
        assertThat(order.getReturnedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should handle duplicate return lines cumulatively")
    void shouldHandleDuplicateReturnLinesCumulatively() {
        ProcessReturnRequest duplicateLines = new ProcessReturnRequest(
                "Two units scanned separately at the returns desk",
                "Both units were inspected during the same intake.",
                List.of(
                        new ProcessReturnRequest.ReturnItemRequest(orderItemId, 1, ItemCondition.SELLABLE),
                        new ProcessReturnRequest.ReturnItemRequest(orderItemId, 1, ItemCondition.DAMAGED)
                )
        );

        ReturnResponse response = returnService.processReturn(
                shippedOrder.id(),
                duplicateLines,
                returnsOperator.getId()
        );

        assertThat(response.items()).hasSize(2);
        assertThat(response.items())
                .extracting(ReturnResponse.ReturnItemResponse::quantity)
                .containsExactly(1, 1);
        assertThat(orderRepository.findById(shippedOrder.id()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.RETURNED);
    }

    @Test
    @DisplayName("Should reject duplicate return lines that exceed the remaining quantity")
    void shouldRejectDuplicateReturnLinesExceedingRemainingQuantity() {
        ProcessReturnRequest duplicateLines = new ProcessReturnRequest(
                "Returns scanner submitted the same item twice",
                "The second scan pushed the total above the shipped quantity.",
                List.of(
                        new ProcessReturnRequest.ReturnItemRequest(orderItemId, 1, ItemCondition.SELLABLE),
                        new ProcessReturnRequest.ReturnItemRequest(orderItemId, 2, ItemCondition.SELLABLE)
                )
        );

        assertThatThrownBy(() -> returnService.processReturn(
                shippedOrder.id(),
                duplicateLines,
                returnsOperator.getId()
        ))
                .isInstanceOf(InvalidReturnQuantityException.class)
                .hasMessageContaining("Cannot return 2 units of SKU 'HOOD-NVY-RET'. Only 1 remaining unreturned units are available.");
    }
}
