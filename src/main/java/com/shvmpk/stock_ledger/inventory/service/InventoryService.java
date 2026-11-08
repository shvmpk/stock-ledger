package com.shvmpk.stock_ledger.inventory.service;

import com.shvmpk.stock_ledger.common.error.InsufficientReservedStockException;
import com.shvmpk.stock_ledger.common.error.InsufficientStockException;
import com.shvmpk.stock_ledger.common.error.ResourceNotFoundException;
import com.shvmpk.stock_ledger.common.error.RetiredProductException;
import com.shvmpk.stock_ledger.common.error.StockAction;
import com.shvmpk.stock_ledger.inventory.entity.StockMovement;
import com.shvmpk.stock_ledger.inventory.entity.StockMovementReason;
import com.shvmpk.stock_ledger.inventory.repository.InventoryRepository;
import com.shvmpk.stock_ledger.inventory.repository.StockMovementRepository;
import com.shvmpk.stock_ledger.product.entity.ProductVariant;
import com.shvmpk.stock_ledger.product.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ProductVariantRepository productVariantRepository;
    private final StockShortageService stockShortageService;

    @Transactional
    public void reserveStock(UUID variantId, int quantity, UUID referenceId, UUID actorId, String notes) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Reservation quantity must be greater than zero");
        }

        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("ProductVariant", variantId));
        String productName = variant.getProduct().getName();

        int updatedRows = inventoryRepository.atomicReserveStock(variantId, quantity);
        if (updatedRows == 0) {
            throw new InsufficientStockException(stockShortageService.describe(
                    variantId,
                    variant.getSku(),
                    quantity,
                    List.of(
                            StockAction.RECEIVE_STOCK,
                            StockAction.REDUCE_QUANTITY,
                            StockAction.VIEW_MOVEMENTS,
                            StockAction.VIEW_RESERVATIONS
                    )
            ));
        }

        StockMovement movement = StockMovement.create(
                variant,
                variant.getSku(),
                productName,
                0,
                quantity,
                StockMovementReason.ORDER_RESERVED,
                "ORDER",
                referenceId,
                actorId,
                notes
        );
        stockMovementRepository.save(movement);
    }

    @Transactional
    public void releaseReservedStock(UUID variantId, int quantity, UUID referenceId, UUID actorId, String notes) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Release quantity must be greater than zero");
        }

        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("ProductVariant", variantId));
        String productName = variant.getProduct().getName();

        int rows = inventoryRepository.atomicReleaseStock(variantId, quantity);
        if (rows == 0) {
            throw new InsufficientReservedStockException(
                    "release",
                    stockShortageService.describe(
                            variantId,
                            variant.getSku(),
                            quantity,
                            List.of(StockAction.VIEW_RESERVATIONS, StockAction.VIEW_MOVEMENTS)
                    )
            );
        }

        StockMovement movement = StockMovement.create(
                variant,
                variant.getSku(),
                productName,
                0,
                -quantity,
                StockMovementReason.ORDER_CANCELLED,
                "ORDER",
                referenceId,
                actorId,
                notes
        );
        stockMovementRepository.save(movement);
    }

    @Transactional
    public void shipStock(UUID variantId, int quantity, UUID referenceId, UUID actorId, String notes) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Shipped quantity must be greater than zero");
        }

        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("ProductVariant", variantId));
        String productName = variant.getProduct().getName();

        int rows = inventoryRepository.atomicDeductForShipment(variantId, quantity);
        if (rows == 0) {
            throw new InsufficientReservedStockException(
                    "ship",
                    stockShortageService.describe(
                            variantId,
                            variant.getSku(),
                            quantity,
                            List.of(StockAction.VIEW_RESERVATIONS, StockAction.VIEW_MOVEMENTS)
                    )
            );
        }

        StockMovement movement = StockMovement.create(
                variant,
                variant.getSku(),
                productName,
                -quantity,
                -quantity,
                StockMovementReason.ORDER_SHIPPED,
                "ORDER",
                referenceId,
                actorId,
                notes
        );
        stockMovementRepository.save(movement);
    }

    @Transactional
    public void receiveStock(UUID variantId, int quantity, UUID referenceId, UUID actorId, String notes) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Received quantity must be greater than zero");
        }

        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("ProductVariant", variantId));
        if (!variant.isSellable()) {
            throw new RetiredProductException(variant.getSku(), "received");
        }
        String productName = variant.getProduct().getName();

        inventoryRepository.findByProductVariantId(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("InventoryLevel for variant", variantId));

        int rows = inventoryRepository.atomicReceiveStock(variantId, quantity);
        if (rows == 0) {
            throw new ResourceNotFoundException("InventoryLevel for variant", variantId);
        }

        StockMovement movement = StockMovement.create(
                variant,
                variant.getSku(),
                productName,
                quantity,
                0,
                StockMovementReason.RECEIVE,
                "MANUAL_RESTOCK",
                referenceId,
                actorId,
                notes
        );
        stockMovementRepository.save(movement);
    }

    @Transactional
    public void restockReturn(UUID variantId, int quantity, UUID returnId, UUID actorId, String notes) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Restock quantity must be greater than zero");
        }

        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("ProductVariant", variantId));
        String productName = variant.getProduct().getName();

        inventoryRepository.findByProductVariantId(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("InventoryLevel for variant", variantId));

        int rows = inventoryRepository.atomicReceiveStock(variantId, quantity);
        if (rows == 0) {
            throw new ResourceNotFoundException("InventoryLevel for variant", variantId);
        }

        StockMovement movement = StockMovement.create(
                variant,
                variant.getSku(),
                productName,
                quantity,
                0,
                StockMovementReason.RETURN_RESTOCK,
                "RETURN",
                returnId,
                actorId,
                notes
        );
        stockMovementRepository.save(movement);
    }

    @Transactional
    public void recordDamagedReturn(UUID variantId, int quantity, UUID returnId, UUID actorId, String notes) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Damaged quantity must be greater than zero");
        }

        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("ProductVariant", variantId));

        StockMovement movement = StockMovement.create(
                variant,
                variant.getSku(),
                variant.getProduct().getName(),
                0,
                0,
                StockMovementReason.RETURN_DAMAGED,
                "RETURN",
                returnId,
                actorId,
                notes
        );
        stockMovementRepository.save(movement);
    }
}
