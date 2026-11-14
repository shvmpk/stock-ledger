package com.shvmpk.stock_ledger.order.service;

import com.shvmpk.stock_ledger.common.error.InsufficientReservedStockException;
import com.shvmpk.stock_ledger.common.error.InvalidOrderStateTransitionException;
import com.shvmpk.stock_ledger.common.error.ResourceNotFoundException;
import com.shvmpk.stock_ledger.common.error.RetiredProductException;
import com.shvmpk.stock_ledger.common.error.StockAction;
import com.shvmpk.stock_ledger.config.security.CurrentUserResolver;
import com.shvmpk.stock_ledger.inventory.service.InventoryService;
import com.shvmpk.stock_ledger.inventory.service.StockShortageService;
import com.shvmpk.stock_ledger.order.dto.CreateOrderRequest;
import com.shvmpk.stock_ledger.order.dto.OrderResponse;
import com.shvmpk.stock_ledger.order.dto.OrderStatusSummaryResponse;
import com.shvmpk.stock_ledger.order.dto.ShipOrderRequest;
import com.shvmpk.stock_ledger.order.entity.Order;
import com.shvmpk.stock_ledger.order.entity.OrderItem;
import com.shvmpk.stock_ledger.order.entity.OrderSource;
import com.shvmpk.stock_ledger.order.entity.OrderStatus;
import com.shvmpk.stock_ledger.order.repository.OrderRepository;
import com.shvmpk.stock_ledger.order.repository.OrderStatusCountProjection;
import com.shvmpk.stock_ledger.product.entity.ProductVariant;
import com.shvmpk.stock_ledger.product.repository.ProductVariantRepository;
import com.shvmpk.stock_ledger.shipping.service.CarrierTrackingResolver;
import com.shvmpk.stock_ledger.shipping.service.ShippingPricingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryService inventoryService;
    private final StockShortageService stockShortageService;
    private final ShippingPricingService shippingPricingService;
    private final CarrierTrackingResolver carrierTrackingResolver;
    private final CurrentUserResolver currentUserResolver;

    @Transactional
    public OrderResponse createManualOrder(CreateOrderRequest request) {
        String orderNumber = generateOrderNumber();
        Order order = Order.create(
                orderNumber,
                OrderSource.MANUAL,
                null,
                request.customerName(),
                request.customerEmail(),
                request.shippingAddress(),
                request.customerShippingCharge()
        );

        for (var itemReq : request.items()) {
            ProductVariant variant = productVariantRepository.findBySkuIgnoreCase(itemReq.sku())
                    .orElseThrow(() -> new ResourceNotFoundException("ProductVariant", itemReq.sku()));
            if (!variant.isSellable()) {
                throw new RetiredProductException(variant.getSku(), "ordered");
            }

            // Reserve inventory atomically
            inventoryService.reserveStock(
                    variant.getId(),
                    itemReq.quantity(),
                    order.getId(),
                    currentUserResolver.currentUserIdOrNull(),
                    "Manual order placed: " + orderNumber
            );

            OrderItem item = OrderItem.create(order, variant, itemReq.quantity(), itemReq.unitPrice());
            order.addItem(item);
        }

        Order saved = orderRepository.save(order);
        return mapToResponse(saved);
    }

    @Transactional
    public OrderResponse packOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new InvalidOrderStateTransitionException(
                    order.getStatus().name(),
                    OrderStatus.READY_TO_SHIP.name()
            );
        }

        order.updateStatus(OrderStatus.READY_TO_SHIP);
        Order saved = orderRepository.save(order);
        return mapToResponse(saved);
    }

    @Transactional
    public OrderResponse shipOrder(UUID orderId, ShipOrderRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        if (order.getStatus() != OrderStatus.READY_TO_SHIP && order.getStatus() != OrderStatus.PENDING) {
            throw new InvalidOrderStateTransitionException(
                    order.getStatus().name(),
                    OrderStatus.SHIPPED.name()
            );
        }

        BigDecimal actualCost = request.actualShippingCostOverride() != null
                ? request.actualShippingCostOverride()
                : shippingPricingService.resolveShippingCost(request.carrier(), request.parcelSize());
        String trackingUrl = carrierTrackingResolver.resolveTrackingUrl(request.carrier(), request.trackingNumber());

        for (OrderItem item : order.getItems()) {
            int reservedQuantity = item.getReservedQuantity();
            if (reservedQuantity <= 0) {
                throw new InsufficientReservedStockException(
                        "ship",
                        stockShortageService.describe(
                                item.getProductVariant().getId(),
                                item.getProductVariant().getSku(),
                                item.getQuantity(),
                                List.of(StockAction.VIEW_RESERVATIONS, StockAction.VIEW_MOVEMENTS)
                        )
                );
            }

            inventoryService.shipStock(
                    item.getProductVariant().getId(),
                    reservedQuantity,
                    order.getId(),
                    currentUserResolver.currentUserIdOrNull(),
                    "Order shipped via " + request.carrier() + " #" + request.trackingNumber()
            );
            item.updateReservedQuantity(0);
        }

        order.markShipped(request.carrier().name(), request.parcelSize().name(), request.trackingNumber(), trackingUrl, actualCost);
        Order saved = orderRepository.save(order);
        return mapToResponse(saved);
    }

    @Transactional
    public OrderResponse cancelOrder(UUID orderId, String reason) {
        return cancelOrder(orderId, reason, currentUserResolver.currentUserIdOrNull());
    }

    @Transactional
    public OrderResponse cancelOrder(UUID orderId, String reason, UUID actorId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        if (order.getStatus() != OrderStatus.PENDING
                && order.getStatus() != OrderStatus.READY_TO_SHIP
                && order.getStatus() != OrderStatus.WAITING_FOR_STOCK) {
            throw new InvalidOrderStateTransitionException(
                    order.getStatus().name(),
                    OrderStatus.CANCELLED.name()
            );
        }

        for (OrderItem item : order.getItems()) {
            int reservedQuantity = item.getReservedQuantity();
            if (reservedQuantity > 0) {
                inventoryService.releaseReservedStock(
                        item.getProductVariant().getId(),
                        reservedQuantity,
                        order.getId(),
                        actorId,
                        "Order cancelled: " + reason
                );
            }
            item.updateReservedQuantity(0);
        }

        order.updateStatus(OrderStatus.CANCELLED);
        Order saved = orderRepository.save(order);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
        return mapToResponse(order);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<OrderResponse> listOrders(OrderStatus status, org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.domain.Page<Order> orders = (status != null)
                ? orderRepository.findAllByStatus(status, pageable)
                : orderRepository.findAll(pageable);
        return orders.map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public List<OrderStatusSummaryResponse> getStatusSummary() {
        Map<OrderStatus, Long> counts = orderRepository.countByStatusGrouped().stream()
                .collect(Collectors.toMap(
                        OrderStatusCountProjection::getStatus,
                        OrderStatusCountProjection::getCount
                ));

        return Arrays.stream(OrderStatus.values())
                .map(status -> new OrderStatusSummaryResponse(
                        status,
                        counts.getOrDefault(status, 0L)
                ))
                .toList();
    }

    private String generateOrderNumber() {
        // The suffix comes from a database sequence, handed out atomically:
        // concurrent orders can never draw the same number, unlike the
        // previous random five-digit draw whose collision rate grew with the
        // stored order history (see V18 migration and LOAD-TESTING.md).
        long suffix = orderRepository.nextOrderNumberSuffix();
        return String.format("ORD-%d-%05d", LocalDate.now().getYear(), suffix);
    }

    public OrderResponse mapToResponse(Order order) {
        List<OrderResponse.OrderItemResponse> itemResponses = order.getItems().stream()
                .map(i -> new OrderResponse.OrderItemResponse(
                        i.getId(),
                        i.getSku(),
                        i.getQuantity(),
                        i.getUnitPrice()
                ))
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getSource().name(),
                order.getStatus(),
                order.getCustomerName(),
                order.getCustomerEmail(),
                order.getShippingAddress(),
                order.getCustomerShippingCharge(),
                order.getActualShippingCost(),
                order.getCarrier(),
                order.getParcelSize(),
                order.getTrackingNumber(),
                order.getTrackingUrl(),
                order.getShippedAt(),
                order.getReturnedAt(),
                itemResponses,
                order.getCreatedAt()
        );
    }
}
