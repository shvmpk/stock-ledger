package com.shvmpk.stock_ledger.order.entity;

import com.shvmpk.stock_ledger.product.entity.ProductVariant;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "order_return_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderReturnItem {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_return_id", nullable = false)
    private OrderReturn orderReturn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_variant_id", nullable = false)
    private ProductVariant productVariant;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ItemCondition condition;

    @Column(nullable = false)
    private boolean restocked;

    public static OrderReturnItem create(
            OrderReturn orderReturn,
            OrderItem orderItem,
            ProductVariant productVariant,
            int quantity,
            ItemCondition condition,
            boolean restocked
    ) {
        OrderReturnItem item = new OrderReturnItem();
        item.id = UUID.randomUUID();
        item.orderReturn = Objects.requireNonNull(orderReturn, "OrderReturn cannot be null");
        item.orderItem = Objects.requireNonNull(orderItem, "OrderItem cannot be null");
        item.productVariant = Objects.requireNonNull(productVariant, "ProductVariant cannot be null");
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
        item.quantity = quantity;
        item.condition = Objects.requireNonNull(condition, "ItemCondition cannot be null");
        item.restocked = restocked;
        return item;
    }
}
