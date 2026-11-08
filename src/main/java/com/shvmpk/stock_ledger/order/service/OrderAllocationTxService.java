package com.shvmpk.stock_ledger.order.service;

import com.shvmpk.stock_ledger.inventory.service.InventoryService;
import com.shvmpk.stock_ledger.order.entity.Order;
import com.shvmpk.stock_ledger.order.entity.OrderItem;
import com.shvmpk.stock_ledger.order.entity.OrderStatus;
import com.shvmpk.stock_ledger.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderAllocationTxService {

    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<String> allocate(UUID orderId, UUID actorId) {
        Optional<Order> maybeOrder = orderRepository.findByIdForUpdate(orderId);
        if (maybeOrder.isEmpty()) {
            return Optional.empty();
        }

        Order order = maybeOrder.get();
        if (order.getStatus() != OrderStatus.WAITING_FOR_STOCK || order.getItems().isEmpty()) {
            return Optional.empty();
        }

        List<AllocationLine> lines = order.getItems().stream()
                .sorted(Comparator.comparing(item -> item.getProductVariant().getId()))
                .map(item -> new AllocationLine(
                        item.getProductVariant().getId(),
                        item.getQuantity()
                ))
                .toList();

        for (AllocationLine line : lines) {
            inventoryService.reserveStock(
                    line.variantId(),
                    line.quantity(),
                    order.getId(),
                    actorId,
                    "FIFO allocation for waiting order " + order.getOrderNumber()
            );
        }

        // Inventory mutations clear the persistence context, so re-lock and
        // update the managed order after all reservations have succeeded.
        Order managedOrder = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalStateException("Order disappeared during allocation"));
        for (OrderItem item : managedOrder.getItems()) {
            item.updateReservedQuantity(item.getQuantity());
        }
        managedOrder.updateStatus(OrderStatus.PENDING);
        orderRepository.saveAndFlush(managedOrder);

        log.info("Allocated waiting order {} ({} item lines).", managedOrder.getOrderNumber(), lines.size());
        return Optional.of(managedOrder.getOrderNumber());
    }

    private record AllocationLine(UUID variantId, int quantity) {
    }
}
