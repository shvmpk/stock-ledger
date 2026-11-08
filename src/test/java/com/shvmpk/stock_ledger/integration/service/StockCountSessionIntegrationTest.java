package com.shvmpk.stock_ledger.integration.service;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import com.shvmpk.stock_ledger.common.error.DomainException;
import com.shvmpk.stock_ledger.common.error.DuplicateStockCountItemException;
import com.shvmpk.stock_ledger.common.error.EmptyStockCountSessionException;
import com.shvmpk.stock_ledger.common.error.InvalidStockCountQuantityException;
import com.shvmpk.stock_ledger.common.error.InvalidStockCountStateException;
import com.shvmpk.stock_ledger.common.error.ResourceNotFoundException;
import com.shvmpk.stock_ledger.inventory.dto.RecordStockCountItemRequest;
import com.shvmpk.stock_ledger.inventory.dto.StockCountItemResponse;
import com.shvmpk.stock_ledger.inventory.dto.StockCountResponse;
import com.shvmpk.stock_ledger.inventory.dto.StockCountSummaryResponse;
import com.shvmpk.stock_ledger.inventory.entity.InventoryLevel;
import com.shvmpk.stock_ledger.inventory.entity.StockCountStatus;
import com.shvmpk.stock_ledger.inventory.entity.StockMovement;
import com.shvmpk.stock_ledger.inventory.entity.StockMovementReason;
import com.shvmpk.stock_ledger.inventory.repository.InventoryRepository;
import com.shvmpk.stock_ledger.inventory.repository.StockCountItemRepository;
import com.shvmpk.stock_ledger.inventory.repository.StockCountRepository;
import com.shvmpk.stock_ledger.inventory.repository.StockMovementRepository;
import com.shvmpk.stock_ledger.inventory.service.InventoryService;
import com.shvmpk.stock_ledger.inventory.service.StockCountService;
import com.shvmpk.stock_ledger.product.entity.Product;
import com.shvmpk.stock_ledger.product.entity.ProductVariant;
import com.shvmpk.stock_ledger.product.repository.ProductRepository;
import com.shvmpk.stock_ledger.user.entity.Role;
import com.shvmpk.stock_ledger.user.entity.User;
import com.shvmpk.stock_ledger.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
@DisplayName("Stock Count Session Integration Tests (Lifecycle, Ledger & Deficits)")
class StockCountSessionIntegrationTest {

    @Autowired
    private StockCountService stockCountService;

    @Autowired
    private StockCountRepository stockCountRepository;

    @Autowired
    private StockCountItemRepository stockCountItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private UserRepository userRepository;

    private User operator;
    private ProductVariant hoodieVariant;
    private ProductVariant teeVariant;

    @BeforeEach
    void setUp() {
        stockCountItemRepository.deleteAll();
        stockCountRepository.deleteAll();
        stockMovementRepository.deleteAll();
        inventoryRepository.deleteAll();
        productRepository.deleteAll();

        operator = userRepository.saveAndFlush(User.create(
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
                "HOOD-NVY-XL",
                "5060123456789",
                "Color / Size",
                "Navy / XL",
                new BigDecimal("28.00"),
                new BigDecimal("68.00"),
                750,
                5
        );
        teeVariant = hoodie.addVariant(
                UUID.randomUUID(),
                "TEE-ECRU-M",
                "5060123456796",
                "Color / Size",
                "Ecru / M",
                new BigDecimal("12.00"),
                new BigDecimal("35.00"),
                220,
                8
        );
        productRepository.saveAndFlush(hoodie);

        inventoryRepository.save(InventoryLevel.initialize(UUID.randomUUID(), hoodieVariant, 12));
        inventoryRepository.save(InventoryLevel.initialize(UUID.randomUUID(), teeVariant, 8));
    }

    @Test
    @DisplayName("Should run the full session lifecycle, apply audit corrections, and record the session reference")
    void shouldRunFullSessionLifecycle() {
        StockCountResponse started = stockCountService.start(operator.getId());

        assertThat(started.status()).isEqualTo(StockCountStatus.IN_PROGRESS);
        assertThat(started.conductedBy()).isNotNull();
        assertThat(started.conductedBy().id()).isEqualTo(operator.getId());
        assertThat(started.conductedBy().fullName()).isEqualTo("Freja Lindholm");
        assertThat(started.conductedBy().email()).isEqualTo("freja.lindholm@nordicliving.se");
        assertThat(started.startedAt()).isNotNull();
        assertThat(started.completedAt()).isNull();
        assertThat(started.items()).isEmpty();

        StockCountResponse recorded = stockCountService.recordItem(
                started.id(),
                new RecordStockCountItemRequest(
                        hoodieVariant.getId(),
                        9,
                        "Cycle count CC-2026-014: three hoodies missing from shelf"
                ),
                operator.getId()
        );

        assertThat(recorded.items()).hasSize(1);
        StockCountItemResponse item = recorded.items().get(0);
        assertThat(item.productVariantId()).isEqualTo(hoodieVariant.getId());
        assertThat(item.sku()).isEqualTo("HOOD-NVY-XL");
        assertThat(item.productName()).isEqualTo("Heavyweight Cotton Hoodie");
        assertThat(item.expectedOnHand()).isEqualTo(12);
        assertThat(item.countedOnHand()).isEqualTo(9);
        assertThat(item.variance()).isEqualTo(-3);
        assertThat(item.notes()).contains("three hoodies missing");
        assertThat(item.hasReservationDeficit()).isFalse();
        assertThat(item.reservationDeficit()).isZero();
        assertThat(recorded.itemCount()).isEqualTo(1);
        assertThat(recorded.totalVariance()).isEqualTo(-3);

        InventoryLevel correctedInventory = inventoryRepository
                .findByProductVariantId(hoodieVariant.getId())
                .orElseThrow();
        assertThat(correctedInventory.getOnHandQuantity()).isEqualTo(9);
        assertThat(correctedInventory.getReservedQuantity()).isZero();

        List<StockMovement> movements = stockMovementRepository.findAllByReferenceId(started.id());
        assertThat(movements).hasSize(1);
        assertThat(movements.get(0).getReason()).isEqualTo(StockMovementReason.AUDIT_CORRECTION);
        assertThat(movements.get(0).getReferenceType()).isEqualTo("STOCK_COUNT");
        assertThat(movements.get(0).getReferenceId()).isEqualTo(started.id());
        assertThat(movements.get(0).getActorId()).isEqualTo(operator.getId());
        assertThat(movements.get(0).getDeltaOnHand()).isEqualTo(-3);

        StockCountResponse completed = stockCountService.complete(started.id());

        assertThat(completed.status()).isEqualTo(StockCountStatus.COMPLETED);
        assertThat(completed.completedAt()).isNotNull();
        assertThat(completed.items()).hasSize(1);
        assertThat(stockCountService.get(started.id()).status())
                .isEqualTo(StockCountStatus.COMPLETED);
    }

    @Test
    @DisplayName("Should reject a duplicate variant without adjusting stock or writing another movement")
    void shouldRejectDuplicateVariantWithoutAdjustingStockAgain() {
        StockCountResponse started = stockCountService.start(operator.getId());
        stockCountService.recordItem(
                started.id(),
                new RecordStockCountItemRequest(hoodieVariant.getId(), 9, "First count of the navy hoodie"),
                operator.getId()
        );

        assertThatThrownBy(() -> stockCountService.recordItem(
                started.id(),
                new RecordStockCountItemRequest(hoodieVariant.getId(), 7, "Accidental rescan of the navy hoodie"),
                operator.getId()
        ))
                .isInstanceOf(DuplicateStockCountItemException.class)
                .hasMessageContaining("HOOD-NVY-XL")
                .extracting(ex -> ((DomainException) ex).getErrorCode())
                .isEqualTo("DUPLICATE_STOCK_COUNT_ITEM");

        InventoryLevel inventory = inventoryRepository.findByProductVariantId(hoodieVariant.getId()).orElseThrow();
        assertThat(inventory.getOnHandQuantity()).isEqualTo(9);
        assertThat(stockMovementRepository.findAllByReferenceId(started.id())).hasSize(1);
        assertThat(stockCountService.get(started.id()).items()).hasSize(1);
    }

    @Test
    @DisplayName("Should reject a negative counted quantity without changing inventory or recording an item")
    void shouldRejectNegativeCountedQuantity() {
        StockCountResponse started = stockCountService.start(operator.getId());

        assertThatThrownBy(() -> stockCountService.recordItem(
                started.id(),
                new RecordStockCountItemRequest(hoodieVariant.getId(), -1, "Invalid negative shelf count"),
                operator.getId()
        ))
                .isInstanceOf(InvalidStockCountQuantityException.class)
                .extracting(ex -> ((DomainException) ex).getErrorCode())
                .isEqualTo("INVALID_STOCK_COUNT_QUANTITY");

        InventoryLevel inventory = inventoryRepository.findByProductVariantId(hoodieVariant.getId()).orElseThrow();
        assertThat(inventory.getOnHandQuantity()).isEqualTo(12);
        assertThat(stockMovementRepository.findAllByReferenceId(started.id())).isEmpty();
        assertThat(stockCountService.get(started.id()).items()).isEmpty();
    }

    @Test
    @DisplayName("Should reject items added to a completed session")
    void shouldRejectItemsForCompletedSession() {
        StockCountResponse started = stockCountService.start(operator.getId());
        stockCountService.recordItem(
                started.id(),
                new RecordStockCountItemRequest(hoodieVariant.getId(), 10, "Shelf count before closing"),
                operator.getId()
        );
        stockCountService.complete(started.id());

        assertThatThrownBy(() -> stockCountService.recordItem(
                started.id(),
                new RecordStockCountItemRequest(teeVariant.getId(), 8, "Late addition to a closed session"),
                operator.getId()
        ))
                .isInstanceOf(InvalidStockCountStateException.class)
                .extracting(ex -> ((DomainException) ex).getErrorCode())
                .isEqualTo("INVALID_STOCK_COUNT_STATE");

        assertThat(stockCountService.get(started.id()).status())
                .isEqualTo(StockCountStatus.COMPLETED);
    }

    @Test
    @DisplayName("Should reject completing a session with no counted items")
    void shouldRejectCompletingEmptySession() {
        StockCountResponse started = stockCountService.start(operator.getId());

        assertThatThrownBy(() -> stockCountService.complete(started.id()))
                .isInstanceOf(EmptyStockCountSessionException.class)
                .extracting(ex -> ((DomainException) ex).getErrorCode())
                .isEqualTo("EMPTY_STOCK_COUNT_SESSION");

        StockCountResponse unchanged = stockCountService.get(started.id());
        assertThat(unchanged.status()).isEqualTo(StockCountStatus.IN_PROGRESS);
        assertThat(unchanged.completedAt()).isNull();
    }

    @Test
    @DisplayName("Should cancel without undoing ledger adjustments or deleting the session")
    void shouldCancelWithoutUndoingAdjustments() {
        StockCountResponse started = stockCountService.start(operator.getId());
        stockCountService.recordItem(
                started.id(),
                new RecordStockCountItemRequest(hoodieVariant.getId(), 9, "Counted before abandoning the session"),
                operator.getId()
        );

        StockCountResponse cancelled = stockCountService.cancel(started.id());

        assertThat(cancelled.status()).isEqualTo(StockCountStatus.CANCELLED);
        assertThat(cancelled.completedAt()).isNotNull();
        assertThat(inventoryRepository.findByProductVariantId(hoodieVariant.getId()).orElseThrow()
                .getOnHandQuantity()).isEqualTo(9);
        assertThat(stockMovementRepository.findAllByReferenceId(started.id())).hasSize(1);
        assertThat(stockCountService.get(started.id()).status()).isEqualTo(StockCountStatus.CANCELLED);

        assertThatThrownBy(() -> stockCountService.recordItem(
                started.id(),
                new RecordStockCountItemRequest(teeVariant.getId(), 8, "Late item on a cancelled session"),
                operator.getId()
        ))
                .isInstanceOf(InvalidStockCountStateException.class)
                .extracting(ex -> ((DomainException) ex).getErrorCode())
                .isEqualTo("INVALID_STOCK_COUNT_STATE");
    }

    @Test
    @DisplayName("Should persist the reservation deficit when physical stock is below reserved stock")
    void shouldPersistReservationDeficit() {
        inventoryService.reserveStock(
                hoodieVariant.getId(),
                5,
                UUID.randomUUID(),
                operator.getId(),
                "Order ORD-2026-1042 reservation"
        );

        StockCountResponse started = stockCountService.start(operator.getId());
        StockCountResponse recorded = stockCountService.recordItem(
                started.id(),
                new RecordStockCountItemRequest(
                        hoodieVariant.getId(),
                        3,
                        "Shelf count found two fewer hoodies than reserved"
                ),
                operator.getId()
        );

        StockCountItemResponse item = recorded.items().get(0);
        assertThat(item.expectedOnHand()).isEqualTo(12);
        assertThat(item.countedOnHand()).isEqualTo(3);
        assertThat(item.variance()).isEqualTo(-9);
        assertThat(item.hasReservationDeficit()).isTrue();
        assertThat(item.reservationDeficit()).isEqualTo(2);
        assertThat(recorded.hasReservationDeficit()).isTrue();
        assertThat(recorded.reservationDeficit()).isEqualTo(2);

        InventoryLevel inventory = inventoryRepository.findByProductVariantId(hoodieVariant.getId()).orElseThrow();
        assertThat(inventory.getOnHandQuantity()).isEqualTo(3);
        assertThat(inventory.getReservedQuantity()).isEqualTo(5);

        stockCountService.complete(started.id());
        StockCountItemResponse persisted = stockCountService.get(started.id()).items().get(0);
        assertThat(persisted.hasReservationDeficit()).isTrue();
        assertThat(persisted.reservationDeficit()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should return 404 for an unknown session or variant")
    void shouldRejectUnknownSessionOrVariant() {
        UUID unknownSessionId = UUID.randomUUID();
        assertThatThrownBy(() -> stockCountService.recordItem(
                unknownSessionId,
                new RecordStockCountItemRequest(hoodieVariant.getId(), 9, "Unknown session"),
                operator.getId()
        ))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(unknownSessionId.toString());

        StockCountResponse started = stockCountService.start(operator.getId());
        UUID unknownVariantId = UUID.randomUUID();
        assertThatThrownBy(() -> stockCountService.recordItem(
                started.id(),
                new RecordStockCountItemRequest(unknownVariantId, 5, "Unknown variant"),
                operator.getId()
        ))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(unknownVariantId.toString());

        assertThat(stockCountService.get(started.id()).items()).isEmpty();
    }

    @Test
    @DisplayName("Should list sessions by status with stable pagination")
    void shouldListSessionsByStatus() {
        StockCountResponse first = stockCountService.start(operator.getId());
        stockCountService.recordItem(
                first.id(),
                new RecordStockCountItemRequest(hoodieVariant.getId(), 11, "First session count"),
                operator.getId()
        );
        stockCountService.complete(first.id());

        StockCountResponse second = stockCountService.start(operator.getId());

        PageRequest pageable = PageRequest.of(
                0,
                10,
                Sort.by(Sort.Order.desc("startedAt"), Sort.Order.desc("id"))
        );
        Page<StockCountSummaryResponse> completed = stockCountService.list(
                StockCountStatus.COMPLETED,
                pageable
        );
        assertThat(completed.getContent()).hasSize(1);
        assertThat(completed.getContent().get(0).id()).isEqualTo(first.id());
        assertThat(completed.getContent().get(0).itemCount()).isEqualTo(1);

        Page<StockCountSummaryResponse> inProgress = stockCountService.list(
                StockCountStatus.IN_PROGRESS,
                pageable
        );
        assertThat(inProgress.getContent()).hasSize(1);
        assertThat(inProgress.getContent().get(0).id()).isEqualTo(second.id());

        Page<StockCountSummaryResponse> all = stockCountService.list(null, pageable);
        assertThat(all.getTotalElements()).isEqualTo(2);
    }
}
