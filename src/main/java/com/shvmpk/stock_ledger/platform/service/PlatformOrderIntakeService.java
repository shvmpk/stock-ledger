package com.shvmpk.stock_ledger.platform.service;

import com.shvmpk.stock_ledger.common.error.ResourceNotFoundException;
import com.shvmpk.stock_ledger.common.error.RetiredProductException;
import com.shvmpk.stock_ledger.order.entity.Order;
import com.shvmpk.stock_ledger.order.entity.OrderItem;
import com.shvmpk.stock_ledger.order.entity.OrderSource;
import com.shvmpk.stock_ledger.order.repository.OrderRepository;
import com.shvmpk.stock_ledger.product.entity.ProductVariant;
import com.shvmpk.stock_ledger.product.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlatformOrderIntakeService {

    private final OrderRepository orderRepository;
    private final ProductVariantRepository productVariantRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<PlatformIntakeResult> createWaitingOrder(
            OrderSource source,
            String orderNumber,
            String externalOrderId,
            String customerName,
            String customerEmail,
            String shippingAddress,
            BigDecimal shippingCharge,
            List<IntakeItem> items
    ) {
        if (orderRepository.existsByOrderNumber(orderNumber)) {
            return Optional.empty();
        }
        if (externalOrderId != null
                && !externalOrderId.isBlank()
                && orderRepository.existsBySourceAndExternalOrderId(source, externalOrderId)) {
            return Optional.empty();
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("A platform order must contain at least one item");
        }

        Order order = Order.createWaiting(
                orderNumber,
                source,
                externalOrderId,
                customerName,
                customerEmail,
                shippingAddress,
                shippingCharge
        );

        List<UUID> variantIds = new ArrayList<>();
        for (IntakeItem item : items) {
            ProductVariant variant = productVariantRepository.findBySkuIgnoreCase(item.sku())
                    .orElseThrow(() -> new ResourceNotFoundException("ProductVariant", item.sku()));
            if (!variant.isSellable()) {
                throw new RetiredProductException(variant.getSku(), "ordered");
            }
            variantIds.add(variant.getId());
            order.addItem(OrderItem.createUnreserved(
                    order,
                    variant,
                    item.quantity(),
                    item.unitPrice()
            ));
        }

        Order savedOrder = orderRepository.saveAndFlush(order);
        return Optional.of(new PlatformIntakeResult(savedOrder.getId(), variantIds));
    }

    public record IntakeItem(String sku, int quantity, BigDecimal unitPrice) {
    }

    public record PlatformIntakeResult(UUID orderId, List<UUID> variantIds) {
    }
}
