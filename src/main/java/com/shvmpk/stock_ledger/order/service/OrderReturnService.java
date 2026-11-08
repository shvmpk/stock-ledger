package com.shvmpk.stock_ledger.order.service;

import com.shvmpk.stock_ledger.common.error.InvalidOrderStateTransitionException;
import com.shvmpk.stock_ledger.common.error.InvalidReturnQuantityException;
import com.shvmpk.stock_ledger.common.error.ResourceNotFoundException;
import com.shvmpk.stock_ledger.inventory.service.InventoryService;
import com.shvmpk.stock_ledger.order.dto.ProcessReturnRequest;
import com.shvmpk.stock_ledger.order.dto.ReturnResponse;
import com.shvmpk.stock_ledger.order.entity.*;
import com.shvmpk.stock_ledger.order.repository.OrderRepository;
import com.shvmpk.stock_ledger.order.repository.OrderReturnRepository;
import com.shvmpk.stock_ledger.user.entity.User;
import com.shvmpk.stock_ledger.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderReturnService {

    private final OrderRepository orderRepository;
    private final OrderReturnRepository orderReturnRepository;
    private final UserRepository userRepository;
    private final InventoryService inventoryService;

    @Transactional
    public ReturnResponse processReturn(UUID orderId, ProcessReturnRequest request, UUID actorId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        if (order.getStatus() != OrderStatus.SHIPPED && order.getStatus() != OrderStatus.PARTIALLY_RETURNED) {
            throw new InvalidOrderStateTransitionException(
                    order.getStatus().name(),
                    OrderStatus.RETURNED.name()
            );
        }

        User actor = actorId != null ? userRepository.findById(actorId).orElse(null) : null;
        OrderReturn orderReturn = OrderReturn.create(
                order,
                actor,
                request.reason(),
                request.operatorNotes()
        );

        // Track already returned quantities across previous returns
        List<OrderReturn> existingReturns = orderReturnRepository.findAllByOrderId(orderId);
        Map<UUID, Integer> previouslyReturnedQuantities = new HashMap<>();
        for (OrderReturn ret : existingReturns) {
            for (OrderReturnItem item : ret.getItems()) {
                previouslyReturnedQuantities.merge(item.getOrderItem().getId(), item.getQuantity(), Integer::sum);
            }
        }

        Map<UUID, OrderItem> orderItemMap = new HashMap<>();
        for (OrderItem oi : order.getItems()) {
            orderItemMap.put(oi.getId(), oi);
        }

        for (ProcessReturnRequest.ReturnItemRequest itemReq : request.items()) {
            OrderItem orderItem = orderItemMap.get(itemReq.orderItemId());
            if (orderItem == null) {
                throw new ResourceNotFoundException("OrderItem", itemReq.orderItemId());
            }

            int alreadyReturned = previouslyReturnedQuantities.getOrDefault(orderItem.getId(), 0);
            if (alreadyReturned + itemReq.quantity() > orderItem.getQuantity()) {
                throw new InvalidReturnQuantityException(
                        orderItem.getSku(),
                        itemReq.quantity(),
                        orderItem.getQuantity() - alreadyReturned
                );
            }
            previouslyReturnedQuantities.put(orderItem.getId(), alreadyReturned + itemReq.quantity());

            boolean restocked = false;
            if (itemReq.condition() == ItemCondition.SELLABLE) {
                inventoryService.restockReturn(
                        orderItem.getProductVariant().getId(),
                        itemReq.quantity(),
                        orderReturn.getId(),
                        actorId,
                        "Return restocked: " + (request.reason() != null ? request.reason() : "SELLABLE")
                );
                restocked = true;
            } else {
                inventoryService.recordDamagedReturn(
                        orderItem.getProductVariant().getId(),
                        itemReq.quantity(),
                        orderReturn.getId(),
                        actorId,
                        "Return damaged written off: " + (request.reason() != null ? request.reason() : "DAMAGED")
                );
            }

            OrderReturnItem returnItem = OrderReturnItem.create(
                    orderReturn,
                    orderItem,
                    orderItem.getProductVariant(),
                    itemReq.quantity(),
                    itemReq.condition(),
                    restocked
            );
            orderReturn.addItem(returnItem);
        }

        OrderReturn savedReturn = orderReturnRepository.save(orderReturn);

        // Check if all items in order have been completely returned
        boolean allReturned = true;
        for (OrderItem oi : order.getItems()) {
            int totalReturned = previouslyReturnedQuantities.getOrDefault(oi.getId(), 0);
            if (totalReturned < oi.getQuantity()) {
                allReturned = false;
                break;
            }
        }

        if (allReturned) {
            order.markReturned();
        } else {
            order.markPartiallyReturned();
        }
        orderRepository.save(order);

        return mapToResponse(savedReturn);
    }

    @Transactional(readOnly = true)
    public List<ReturnResponse> getReturnsForOrder(UUID orderId) {
        if (!orderRepository.existsById(orderId)) {
            throw new ResourceNotFoundException("Order", orderId);
        }

        return orderReturnRepository.findAllByOrderIdOrderByProcessedAtAscIdAsc(orderId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    private ReturnResponse mapToResponse(OrderReturn orderReturn) {
        List<ReturnResponse.ReturnItemResponse> itemResponses = orderReturn.getItems().stream()
                .map(i -> new ReturnResponse.ReturnItemResponse(
                        i.getId(),
                        i.getOrderItem().getId(),
                        i.getOrderItem().getSku(),
                        i.getQuantity(),
                        i.getCondition(),
                        i.isRestocked()
                ))
                .toList();

        ReturnResponse.ProcessedByResponse processedBy = null;
        if (orderReturn.getProcessedBy() != null) {
            processedBy = new ReturnResponse.ProcessedByResponse(
                    orderReturn.getProcessedBy().getId(),
                    orderReturn.getProcessedBy().getFullName(),
                    orderReturn.getProcessedBy().getEmail()
            );
        }

        return new ReturnResponse(
                orderReturn.getId(),
                orderReturn.getOrder().getId(),
                orderReturn.getReturnReason(),
                orderReturn.getOperatorNotes(),
                processedBy,
                itemResponses,
                orderReturn.getProcessedAt()
        );
    }
}
