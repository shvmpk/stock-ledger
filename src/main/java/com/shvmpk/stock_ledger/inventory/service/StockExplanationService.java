package com.shvmpk.stock_ledger.inventory.service;

import com.shvmpk.stock_ledger.common.error.ResourceNotFoundException;
import com.shvmpk.stock_ledger.inventory.dto.MovementSummaryResponse;
import com.shvmpk.stock_ledger.inventory.dto.ReservationHolderResponse;
import com.shvmpk.stock_ledger.inventory.dto.StockExplanationResponse;
import com.shvmpk.stock_ledger.inventory.entity.InventoryLevel;
import com.shvmpk.stock_ledger.inventory.entity.StockMovementReason;
import com.shvmpk.stock_ledger.inventory.repository.InventoryRepository;
import com.shvmpk.stock_ledger.inventory.repository.StockMovementRepository;
import com.shvmpk.stock_ledger.order.repository.OrderRepository;
import com.shvmpk.stock_ledger.product.entity.ProductVariant;
import com.shvmpk.stock_ledger.product.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockExplanationService {

    static final int MOVEMENT_WINDOW_DAYS = 90;

    private final ProductVariantRepository productVariantRepository;
    private final InventoryRepository inventoryRepository;
    private final OrderRepository orderRepository;
    private final StockMovementRepository stockMovementRepository;
    private final StockExplanationComposer composer;

    @Transactional(readOnly = true)
    public StockExplanationResponse explain(UUID variantId) {
        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("ProductVariant", variantId));
        InventoryLevel level = inventoryRepository.findByProductVariantId(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("InventoryLevel", variantId));

        OffsetDateTime generatedAt = OffsetDateTime.now();
        OffsetDateTime windowStart = generatedAt.minusDays(MOVEMENT_WINDOW_DAYS);

        List<ReservationHolderResponse> reservations = orderRepository.findOpenReservationHolders(variantId).stream()
                .map(holder -> new ReservationHolderResponse(
                        holder.getOrderId(),
                        holder.getOrderNumber(),
                        holder.getStatus(),
                        holder.getReservedQuantity(),
                        holder.getOrderDate()
                ))
                .toList();

        List<MovementSummaryResponse> movements = stockMovementRepository
                .summariseByReason(variantId, windowStart)
                .stream()
                .map(summary -> new MovementSummaryResponse(
                        summary.getReason(),
                        label(summary.getReason()),
                        (int) summary.getMovementCount(),
                        (int) summary.getNetOnHand(),
                        (int) summary.getNetReserved(),
                        summary.getLatestAt()
                ))
                .toList();

        StockExplanationResponse draft = new StockExplanationResponse(
                variant.getId(),
                variant.getSku(),
                variant.getProduct().getName(),
                variant.getOptionName(),
                variant.getOptionValue(),
                level.getOnHandQuantity(),
                level.getReservedQuantity(),
                level.getAvailableQuantity(),
                variant.getMinStockThreshold(),
                variant.isSellable(),
                MOVEMENT_WINDOW_DAYS,
                windowStart,
                reservations,
                movements,
                List.of(),
                generatedAt
        );

        return new StockExplanationResponse(
                draft.productVariantId(),
                draft.sku(),
                draft.productName(),
                draft.optionName(),
                draft.optionValue(),
                draft.onHandQuantity(),
                draft.reservedQuantity(),
                draft.availableQuantity(),
                draft.reorderLevel(),
                draft.sellable(),
                draft.movementWindowDays(),
                draft.windowStart(),
                draft.reservations(),
                draft.recentMovements(),
                composer.compose(draft),
                draft.generatedAt()
        );
    }

    private String label(StockMovementReason reason) {
        return switch (reason) {
            case RECEIVE -> "Received";
            case ORDER_RESERVED -> "Reserved for orders";
            case ORDER_SHIPPED -> "Shipped";
            case ORDER_CANCELLED -> "Reservations released";
            case RETURN_RESTOCK -> "Returns restocked";
            case RETURN_DAMAGED -> "Returns written off";
            case AUDIT_CORRECTION -> "Stock count corrections";
        };
    }
}
