package com.shvmpk.stock_ledger.order.service;

import com.shvmpk.stock_ledger.common.error.DuplicateResourceException;
import com.shvmpk.stock_ledger.common.error.ResourceNotFoundException;
import com.shvmpk.stock_ledger.common.error.RetiredProductException;
import com.shvmpk.stock_ledger.order.entity.Order;
import com.shvmpk.stock_ledger.order.entity.OrderItem;
import com.shvmpk.stock_ledger.order.entity.OrderSource;
import com.shvmpk.stock_ledger.order.repository.OrderRepository;
import com.shvmpk.stock_ledger.product.entity.ProductVariant;
import com.shvmpk.stock_ledger.product.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class OrderRowImportProcessor {

    private final OrderRepository orderRepository;
    private final ProductVariantRepository productVariantRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Order createWaitingOrder(
            String orderNumber,
            String customerName,
            String customerEmail,
            String shippingAddress,
            BigDecimal shippingCharge,
            String sku,
            int quantity,
            BigDecimal unitPrice
    ) {
        if (orderRepository.existsByOrderNumber(orderNumber)) {
            throw new DuplicateResourceException("Order", "order number", orderNumber);
        }

        ProductVariant variant = productVariantRepository.findBySkuIgnoreCase(sku)
                .orElseThrow(() -> new ResourceNotFoundException("ProductVariant", sku));
        if (!variant.isSellable()) {
            throw new RetiredProductException(variant.getSku(), "ordered");
        }

        Order order = Order.createWaiting(
                orderNumber,
                OrderSource.CSV,
                orderNumber,
                customerName,
                customerEmail,
                shippingAddress,
                shippingCharge
        );
        order.addItem(OrderItem.createUnreserved(order, variant, quantity, unitPrice));

        return orderRepository.saveAndFlush(order);
    }
}
