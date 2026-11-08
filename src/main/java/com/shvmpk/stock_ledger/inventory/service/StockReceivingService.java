package com.shvmpk.stock_ledger.inventory.service;

import com.shvmpk.stock_ledger.common.error.ResourceNotFoundException;
import com.shvmpk.stock_ledger.inventory.dto.ReceiveStockResult;
import com.shvmpk.stock_ledger.inventory.dto.StockLevelResponse;
import com.shvmpk.stock_ledger.inventory.entity.InventoryLevel;
import com.shvmpk.stock_ledger.inventory.repository.InventoryRepository;
import com.shvmpk.stock_ledger.order.service.BacklogAllocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockReceivingService {

    private final InventoryService inventoryService;
    private final InventoryRepository inventoryRepository;
    private final BacklogAllocationService backlogAllocationService;

    public ReceiveStockResult receive(UUID variantId, int quantity, UUID actorId, String notes) {
        UUID referenceId = UUID.randomUUID();
        inventoryService.receiveStock(variantId, quantity, referenceId, actorId, notes);

        List<String> allocatedOrderNumbers;
        try {
            allocatedOrderNumbers = backlogAllocationService.allocateWaitingForVariant(variantId, actorId);
        } catch (RuntimeException ex) {
            log.error(
                    "Stock receipt {} committed for variant {}, but backlog allocation failed. "
                            + "The receipt remains valid and the next allocation sweep will retry.",
                    referenceId,
                    variantId,
                    ex
            );
            allocatedOrderNumbers = List.of();
        }

        InventoryLevel level = inventoryRepository.findByProductVariantId(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("InventoryLevel", variantId));

        return new ReceiveStockResult(StockLevelResponse.from(level), allocatedOrderNumbers);
    }
}
