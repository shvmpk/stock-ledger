package com.shvmpk.stock_ledger.inventory.entity;

import com.shvmpk.stock_ledger.product.entity.ProductVariant;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "stock_count_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_stock_count_items_session_variant",
                columnNames = {"stock_count_id", "product_variant_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockCountItem {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_count_id", nullable = false)
    private StockCount stockCount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_variant_id", nullable = false)
    private ProductVariant productVariant;

    @Column(name = "expected_on_hand", nullable = false)
    private int expectedOnHand;

    @Column(name = "counted_on_hand", nullable = false)
    private int countedOnHand;

    @Column(nullable = false)
    private int variance;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "has_reservation_deficit", nullable = false)
    private boolean hasReservationDeficit;

    @Column(name = "reservation_deficit", nullable = false)
    private int reservationDeficit;

    public static StockCountItem create(
            StockCount stockCount,
            ProductVariant productVariant,
            int expectedOnHand,
            int countedOnHand,
            int variance,
            String notes,
            boolean hasReservationDeficit,
            int reservationDeficit
    ) {
        StockCountItem item = new StockCountItem();
        item.id = UUID.randomUUID();
        item.stockCount = Objects.requireNonNull(stockCount, "Stock count session cannot be null");
        item.productVariant = Objects.requireNonNull(productVariant, "Product variant cannot be null");
        item.expectedOnHand = expectedOnHand;
        item.countedOnHand = countedOnHand;
        item.variance = variance;
        item.notes = notes;
        item.hasReservationDeficit = hasReservationDeficit;
        item.reservationDeficit = reservationDeficit;
        return item;
    }
}
