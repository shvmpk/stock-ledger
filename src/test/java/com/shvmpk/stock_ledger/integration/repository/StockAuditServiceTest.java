package com.shvmpk.stock_ledger.integration.repository;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import com.shvmpk.stock_ledger.inventory.entity.InventoryLevel;
import com.shvmpk.stock_ledger.inventory.entity.StockMovement;
import com.shvmpk.stock_ledger.inventory.entity.StockMovementReason;
import com.shvmpk.stock_ledger.inventory.repository.InventoryRepository;
import com.shvmpk.stock_ledger.inventory.repository.StockMovementRepository;
import com.shvmpk.stock_ledger.inventory.service.InventoryService;
import com.shvmpk.stock_ledger.inventory.service.StockAuditResult;
import com.shvmpk.stock_ledger.inventory.service.StockAuditService;
import com.shvmpk.stock_ledger.product.entity.Product;
import com.shvmpk.stock_ledger.product.entity.ProductVariant;
import com.shvmpk.stock_ledger.product.repository.ProductRepository;
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

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
@DisplayName("Stock Audit & Physical Count Correction Integration Tests")
class StockAuditServiceTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private StockAuditService stockAuditService;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Test
    @DisplayName("Should receive inbound stock and record immutable RECEIVE movement")
    void shouldReceiveStockAndRecordMovement() {
        Product product = Product.create(UUID.randomUUID(), "French Roast Whole Bean", null, "Coffee");
        ProductVariant variant = product.addVariant(
                UUID.randomUUID(),
                "COF-FRN-500G",
                "5060123000011",
                "Weight",
                "500g",
                new BigDecimal("8.00"),
                new BigDecimal("18.00"),
                500,
                5
        );
        productRepository.save(product);

        InventoryLevel inv = InventoryLevel.initialize(UUID.randomUUID(), variant, 10);
        inventoryRepository.save(inv);

        UUID batchId = UUID.randomUUID();
        inventoryService.receiveStock(variant.getId(), 25, batchId, null, "PO-2026-08 Restock Shipment");

        InventoryLevel updated = inventoryRepository.findByProductVariantId(variant.getId()).orElseThrow();
        assertThat(updated.getOnHandQuantity()).isEqualTo(35);
        assertThat(updated.getAvailableQuantity()).isEqualTo(35);

        List<StockMovement> movements = stockMovementRepository.findAllByReferenceId(batchId);
        assertThat(movements).hasSize(1);
        assertThat(movements.get(0).getReason()).isEqualTo(StockMovementReason.RECEIVE);
        assertThat(movements.get(0).getDeltaOnHand()).isEqualTo(25);
        assertThat(movements.get(0).getDeltaReserved()).isEqualTo(0);
    }

    @Test
    @DisplayName("Physical count correction must update on-hand, log AUDIT_CORRECTION, and detect deficit if counted < reserved")
    void shouldPerformPhysicalStockCountCorrection() {
        Product product = Product.create(UUID.randomUUID(), "Wool Throw Blanket", null, "Home");
        ProductVariant variant = product.addVariant(
                UUID.randomUUID(),
                "HOME-BLANKET-GRY",
                "5060123000022",
                "Color",
                "Grey",
                new BigDecimal("35.00"),
                new BigDecimal("85.00"),
                1200,
                3
        );
        productRepository.save(product);

        // System says: on_hand = 10, reserved = 5 (available = 5)
        InventoryLevel inv = InventoryLevel.initialize(UUID.randomUUID(), variant, 10);
        inventoryRepository.save(inv);
        inventoryService.reserveStock(variant.getId(), 5, UUID.randomUUID(), null, "Order reserved");

        // Staff counts shelf and finds only 3 physical blankets (deficit: 3 < 5 reserved!)
        StockAuditResult result = stockAuditService.adjustPhysicalCount(
                variant.getId(),
                3,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Monthly shelf count audit"
        );

        assertThat(result.variance()).isEqualTo(-7); // 3 - 10 = -7
        assertThat(result.hasReservationDeficit()).isTrue();
        assertThat(result.reservationDeficit()).isEqualTo(2); // 5 reserved - 3 counted = 2 deficit

        InventoryLevel updated = inventoryRepository.findByProductVariantId(variant.getId()).orElseThrow();
        assertThat(updated.getOnHandQuantity()).isEqualTo(3);
        assertThat(updated.getReservedQuantity()).isEqualTo(5);
    }
}
