package com.shvmpk.stock_ledger.order.entity;

import com.shvmpk.stock_ledger.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "order_returns")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderReturn {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_by")
    private User processedBy;

    @Column(name = "return_reason")
    private String returnReason;

    @Column(name = "operator_notes", columnDefinition = "TEXT")
    private String operatorNotes;

    @OneToMany(mappedBy = "orderReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderReturnItem> items = new ArrayList<>();

    @Column(name = "processed_at", nullable = false, updatable = false)
    private OffsetDateTime processedAt;

    public static OrderReturn create(
            Order order,
            User processedBy,
            String returnReason,
            String operatorNotes
    ) {
        OrderReturn ret = new OrderReturn();
        ret.id = UUID.randomUUID();
        ret.order = Objects.requireNonNull(order, "Order cannot be null");
        ret.processedBy = processedBy;
        ret.returnReason = returnReason;
        ret.operatorNotes = operatorNotes;
        ret.processedAt = OffsetDateTime.now();
        return ret;
    }

    public void addItem(OrderReturnItem item) {
        items.add(item);
    }
}
