package com.shvmpk.stock_ledger.integration.service;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import com.shvmpk.stock_ledger.common.error.InsufficientReservedStockException;
import com.shvmpk.stock_ledger.common.error.ResourceNotFoundException;
import com.shvmpk.stock_ledger.inventory.entity.InventoryLevel;
import com.shvmpk.stock_ledger.inventory.entity.StockMovement;
import com.shvmpk.stock_ledger.inventory.entity.StockMovementReason;
import com.shvmpk.stock_ledger.inventory.repository.InventoryRepository;
import com.shvmpk.stock_ledger.inventory.repository.StockMovementRepository;
import com.shvmpk.stock_ledger.inventory.service.InventoryService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
@DisplayName("Inventory Mutation Integrity Tests")
class InventoryMutationIntegrityTest {

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
    @DisplayName("Should reject releasing more reserved stock than available and write no movement")
    void shouldRejectReleaseAboveReserved() {
        ProductVariant variant = createVariant("SOCK-MRN-L", "5060123456799");
        inventoryRepository.save(InventoryLevel.initialize(UUID.randomUUID(), variant, 12));
        inventoryService.reserveStock(
                variant.getId(), 4, UUID.randomUUID(), null, "Order ORD-2026-1042 reservation");

        UUID cancellationId = UUID.randomUUID();

        assertThatThrownBy(() -> inventoryService.releaseReservedStock(
                variant.getId(), 5, cancellationId, null, "Order ORD-2026-1042 cancelled"))
                .isInstanceOf(InsufficientReservedStockException.class)
                .hasMessageContaining("Cannot release 5 units of SKU 'SOCK-MRN-L'");

        InventoryLevel unchanged = inventoryRepository.findByProductVariantId(variant.getId()).orElseThrow();
        assertThat(unchanged.getReservedQuantity()).isEqualTo(4);
        assertThat(stockMovementRepository.findAllByReferenceId(cancellationId)).isEmpty();
    }

    @Test
    @DisplayName("Should reject receiving stock when the inventory level does not exist")
    void shouldRejectReceiveWithoutInventoryLevel() {
        ProductVariant variant = createVariant("SOCK-MRN-M", "5060123456805");
        UUID receiptId = UUID.randomUUID();

        assertThatThrownBy(() -> inventoryService.receiveStock(
                variant.getId(), 24, receiptId, null, "PO-2026-09 inbound receipt"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("InventoryLevel for variant");

        assertThat(stockMovementRepository.findAllByReferenceId(receiptId)).isEmpty();
    }

    @Test
    @DisplayName("Should reject restocking a return when the inventory level does not exist")
    void shouldRejectRestockWithoutInventoryLevel() {
        ProductVariant variant = createVariant("SOCK-MRN-S", "5060123456812");
        UUID returnId = UUID.randomUUID();

        assertThatThrownBy(() -> inventoryService.restockReturn(
                variant.getId(), 2, returnId, null, "Return RTN-2026-0088 restocked"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("InventoryLevel for variant");

        assertThat(stockMovementRepository.findAllByReferenceId(returnId)).isEmpty();
    }

    @Test
    @DisplayName("Should reject a physical count when the inventory level does not exist")
    void shouldRejectCountWithoutInventoryLevel() {
        ProductVariant variant = createVariant("SOCK-MRN-XL", "5060123456829");
        UUID countSessionId = UUID.randomUUID();

        assertThatThrownBy(() -> stockAuditService.adjustPhysicalCount(
                variant.getId(), 9, countSessionId, null, "Cycle count CC-2026-014"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("InventoryLevel for variant");
    }

    @Test
    @DisplayName("Should record the stock count session id as the AUDIT_CORRECTION reference")
    void shouldRecordStockCountReferenceOnAuditMovement() {
        ProductVariant variant = createVariant("SOCK-MRN-XS", "5060123456836");
        inventoryRepository.save(InventoryLevel.initialize(UUID.randomUUID(), variant, 16));
        UUID countSessionId = UUID.randomUUID();

        stockAuditService.adjustPhysicalCount(
                variant.getId(), 13, countSessionId, null, "Cycle count CC-2026-015");

        List<StockMovement> movements = stockMovementRepository.findAllByReferenceId(countSessionId);
        assertThat(movements).hasSize(1);
        assertThat(movements.get(0).getReason()).isEqualTo(StockMovementReason.AUDIT_CORRECTION);
        assertThat(movements.get(0).getDeltaOnHand()).isEqualTo(-3);
    }

    private ProductVariant createVariant(String sku, String barcode) {
        Product product = Product.create(
                UUID.randomUUID(),
                "Merino Wool Crew Sock",
                "Fine-gauge merino with a reinforced heel",
                "Apparel"
        );
        ProductVariant variant = product.addVariant(
                UUID.randomUUID(),
                sku,
                barcode,
                "Size",
                "L",
                new BigDecimal("6.50"),
                new BigDecimal("18.00"),
                80,
                12
        );
        productRepository.save(product);
        return variant;
    }
}
