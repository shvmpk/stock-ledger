package com.shvmpk.stock_ledger.order.service;

import com.shvmpk.stock_ledger.common.error.InsufficientStockException;
import com.shvmpk.stock_ledger.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BacklogAllocationService {

    static final int BATCH_SIZE = 100;

    private final OrderRepository orderRepository;
    private final OrderAllocationTxService orderAllocationTxService;

    public List<String> allocateWaitingForVariant(UUID variantId, UUID actorId) {
        List<UUID> waitingOrderIds = orderRepository.findWaitingOrderIdsByVariant(
                variantId,
                PageRequest.of(0, BATCH_SIZE)
        );
        return allocate(waitingOrderIds, actorId);
    }

    public List<String> allocateAllWaiting(UUID actorId) {
        List<UUID> waitingOrderIds = orderRepository.findAllWaitingOrderIds(
                PageRequest.of(0, BATCH_SIZE)
        );
        return allocate(waitingOrderIds, actorId);
    }

    private List<String> allocate(List<UUID> waitingOrderIds, UUID actorId) {
        List<String> allocatedOrderNumbers = new ArrayList<>();
        for (UUID orderId : waitingOrderIds) {
            try {
                orderAllocationTxService.allocate(orderId, actorId)
                        .ifPresent(allocatedOrderNumbers::add);
            } catch (InsufficientStockException ex) {
                log.debug("Waiting order {} is still short of stock: {}", orderId, ex.getMessage());
            }
        }
        return allocatedOrderNumbers;
    }
}
