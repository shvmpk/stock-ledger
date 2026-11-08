package com.shvmpk.stock_ledger.order.entity;

import com.shvmpk.stock_ledger.product.entity.ProductVariant;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_variant_id", nullable = false)
    private ProductVariant productVariant;

    @Column(nullable = false, length = 100)
    private String sku;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    public static OrderItem create(Order order, ProductVariant productVariant, int quantity, BigDecimal unitPrice) {
        OrderItem item = new OrderItem();
        item.id = UUID.randomUUID();
        item.order = Objects.requireNonNull(order, "Order cannot be null");
        item.productVariant = Objects.requireNonNull(productVariant, "ProductVariant cannot be null");
        item.sku = productVariant.getSku();
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
        item.quantity = quantity;
        item.reservedQuantity = quantity;
        item.unitPrice = Objects.requireNonNull(unitPrice, "UnitPrice cannot be null");
        return item;
    }

    public static OrderItem createUnreserved(
            Order order,
            ProductVariant productVariant,
            int quantity,
            BigDecimal unitPrice
    ) {
        OrderItem item = create(order, productVariant, quantity, unitPrice);
        item.reservedQuantity = 0;
        return item;
    }

    public void updateReservedQuantity(int reservedQuantity) {
        this.reservedQuantity = reservedQuantity;
    }
}
