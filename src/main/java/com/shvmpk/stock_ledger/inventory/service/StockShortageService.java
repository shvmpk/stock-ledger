package com.shvmpk.stock_ledger.inventory.service;

import com.shvmpk.stock_ledger.common.error.ReservationHolder;
import com.shvmpk.stock_ledger.common.error.StockAction;
import com.shvmpk.stock_ledger.common.error.StockShortageDetails;
import com.shvmpk.stock_ledger.inventory.entity.InventoryLevel;
import com.shvmpk.stock_ledger.inventory.repository.InventoryRepository;
import com.shvmpk.stock_ledger.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Builds the evidence behind a stock refusal: the current ledger position and
 * the open orders holding reservations for the variant.
 */
@Service
@RequiredArgsConstructor
public class StockShortageService {

    private final InventoryRepository inventoryRepository;
    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public StockShortageDetails describe(
            UUID variantId,
            String sku,
            int requestedQuantity,
            List<StockAction> actions
    ) {
        InventoryLevel level = inventoryRepository.findByProductVariantId(variantId).orElse(null);
        int onHand = level != null ? level.getOnHandQuantity() : 0;
        int reserved = level != null ? level.getReservedQuantity() : 0;
        int available = level != null ? level.getAvailableQuantity() : 0;

        List<ReservationHolder> reservedBy = orderRepository.findOpenReservationHolders(variantId).stream()
                .map(holder -> new ReservationHolder(
                        holder.getOrderId(),
                        holder.getOrderNumber(),
                        holder.getReservedQuantity(),
                        holder.getOrderDate()
                ))
                .toList();

        return new StockShortageDetails(
                sku,
                requestedQuantity,
                available,
                onHand,
                reserved,
                reservedBy,
                actions
        );
    }
}
