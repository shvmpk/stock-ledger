package com.shvmpk.stock_ledger.order.entity;

import com.shvmpk.stock_ledger.common.error.InvalidOrderStateTransitionException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_number", nullable = false, unique = true, length = 100)
    private String orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private OrderSource source;

    @Column(name = "external_order_id", length = 100)
    private String externalOrderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private OrderStatus status;

    @Column(name = "customer_name", nullable = false, length = 150)
    private String customerName;

    @Column(name = "customer_email")
    private String customerEmail;

    @Column(name = "shipping_address", nullable = false, columnDefinition = "TEXT")
    private String shippingAddress;

    @Column(name = "customer_shipping_charge", nullable = false, precision = 10, scale = 2)
    private BigDecimal customerShippingCharge;

    @Column(name = "actual_shipping_cost", precision = 10, scale = 2)
    private BigDecimal actualShippingCost;

    @Column(length = 50)
    private String carrier;

    @Column(name = "parcel_size", length = 50)
    private String parcelSize;

    @Column(name = "tracking_number", length = 100)
    private String trackingNumber;

    @Column(name = "tracking_url", columnDefinition = "TEXT")
    private String trackingUrl;

    @Column(name = "shipped_at")
    private OffsetDateTime shippedAt;

    @Column(name = "returned_at")
    private OffsetDateTime returnedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static Order create(
            String orderNumber,
            OrderSource source,
            String externalOrderId,
            String customerName,
            String customerEmail,
            String shippingAddress,
            BigDecimal customerShippingCharge
    ) {
        Order order = new Order();
        order.id = UUID.randomUUID();
        order.orderNumber = Objects.requireNonNull(orderNumber, "OrderNumber cannot be null");
        order.source = Objects.requireNonNull(source, "OrderSource cannot be null");
        order.externalOrderId = externalOrderId;
        order.status = OrderStatus.PENDING;
        order.customerName = Objects.requireNonNull(customerName, "CustomerName cannot be null");
        order.customerEmail = customerEmail;
        order.shippingAddress = Objects.requireNonNull(shippingAddress, "ShippingAddress cannot be null");
        order.customerShippingCharge = customerShippingCharge != null ? customerShippingCharge : BigDecimal.ZERO;
        order.createdAt = OffsetDateTime.now();
        order.updatedAt = OffsetDateTime.now();
        return order;
    }

    public static Order createWaiting(
            String orderNumber,
            OrderSource source,
            String externalOrderId,
            String customerName,
            String customerEmail,
            String shippingAddress,
            BigDecimal customerShippingCharge
    ) {
        Order order = create(
                orderNumber,
                source,
                externalOrderId,
                customerName,
                customerEmail,
                shippingAddress,
                customerShippingCharge
        );
        order.markWaitingForStock();
        return order;
    }

    public void addItem(OrderItem item) {
        items.add(item);
    }

    public void markWaitingForStock() {
        if (status != OrderStatus.PENDING) {
            throw new InvalidOrderStateTransitionException(
                    status.name(),
                    OrderStatus.WAITING_FOR_STOCK.name()
            );
        }
        this.status = OrderStatus.WAITING_FOR_STOCK;
        this.updatedAt = OffsetDateTime.now();
    }

    public void updateStatus(OrderStatus newStatus) {
        this.status = Objects.requireNonNull(newStatus, "Status cannot be null");
        this.updatedAt = OffsetDateTime.now();
    }

    public void markShipped(String carrier, String parcelSize, String trackingNumber, String trackingUrl, BigDecimal actualCost) {
        this.status = OrderStatus.SHIPPED;
        this.carrier = carrier;
        this.parcelSize = parcelSize;
        this.trackingNumber = trackingNumber;
        this.trackingUrl = trackingUrl;
        this.actualShippingCost = actualCost;
        this.shippedAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    public void markReturned() {
        this.status = OrderStatus.RETURNED;
        this.returnedAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    public void markPartiallyReturned() {
        this.status = OrderStatus.PARTIALLY_RETURNED;
        this.returnedAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }
}
