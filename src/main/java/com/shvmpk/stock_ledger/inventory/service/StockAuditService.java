package com.shvmpk.stock_ledger.inventory.service;

import com.shvmpk.stock_ledger.common.error.ResourceNotFoundException;
import com.shvmpk.stock_ledger.inventory.entity.InventoryLevel;
import com.shvmpk.stock_ledger.inventory.entity.StockMovement;
import com.shvmpk.stock_ledger.inventory.entity.StockMovementReason;
import com.shvmpk.stock_ledger.inventory.repository.InventoryRepository;
import com.shvmpk.stock_ledger.inventory.repository.StockMovementRepository;
import com.shvmpk.stock_ledger.product.entity.ProductVariant;
import com.shvmpk.stock_ledger.product.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockAuditService {

    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ProductVariantRepository productVariantRepository;

    @Transactional
    public StockAuditResult adjustPhysicalCount(
            UUID variantId,
            int countedOnHand,
            UUID referenceId,
            UUID actorId,
            String notes
    ) {
        if (countedOnHand < 0) {
            throw new IllegalArgumentException("Physical count cannot be negative");
        }

        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("ProductVariant", variantId));
        String productName = variant.getProduct().getName();

        InventoryLevel currentInventory = inventoryRepository.findByProductVariantId(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("InventoryLevel for variant", variantId));

        int previousOnHand = currentInventory.getOnHandQuantity();
        int variance = countedOnHand - previousOnHand;
        int reservedQuantity = currentInventory.getReservedQuantity();

        boolean hasDeficit = countedOnHand < reservedQuantity;
        int deficit = hasDeficit ? (reservedQuantity - countedOnHand) : 0;

        int rows = inventoryRepository.atomicCorrectOnHand(variantId, countedOnHand);
        if (rows == 0) {
            throw new ResourceNotFoundException("InventoryLevel for variant", variantId);
        }

        if (variance != 0) {
            StockMovement movement = StockMovement.create(
                    variant,
                    variant.getSku(),
                    productName,
                    variance,
                    0,
                    StockMovementReason.AUDIT_CORRECTION,
                    "STOCK_COUNT",
                    referenceId,
                    actorId,
                    notes
            );
            stockMovementRepository.save(movement);
        }

        return new StockAuditResult(
                variantId,
                previousOnHand,
                countedOnHand,
                variance,
                hasDeficit,
                deficit
        );
    }
}
