package com.shvmpk.stock_ledger.inventory.entity;

import com.shvmpk.stock_ledger.common.error.EmptyStockCountSessionException;
import com.shvmpk.stock_ledger.common.error.InvalidStockCountStateException;
import com.shvmpk.stock_ledger.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "stock_counts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockCount {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private StockCountStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conducted_by")
    private User conductedBy;

    @Column(name = "started_at", nullable = false, updatable = false)
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @BatchSize(size = 50)
    @OneToMany(mappedBy = "stockCount", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StockCountItem> items = new ArrayList<>();

    public static StockCount start(User conductedBy) {
        StockCount stockCount = new StockCount();
        stockCount.id = UUID.randomUUID();
        stockCount.status = StockCountStatus.IN_PROGRESS;
        stockCount.conductedBy = conductedBy;
        stockCount.startedAt = OffsetDateTime.now();
        return stockCount;
    }

    public void addItem(StockCountItem item) {
        requireInProgress();
        StockCountItem requiredItem = Objects.requireNonNull(item, "Stock count item cannot be null");
        if (requiredItem.getStockCount() != this) {
            throw new IllegalArgumentException("Stock count item belongs to a different session");
        }
        this.items.add(requiredItem);
    }

    public void complete() {
        requireInProgress();
        if (this.items.isEmpty()) {
            throw new EmptyStockCountSessionException();
        }
        this.status = StockCountStatus.COMPLETED;
        this.completedAt = OffsetDateTime.now();
    }

    public void cancel() {
        requireInProgress();
        this.status = StockCountStatus.CANCELLED;
        this.completedAt = OffsetDateTime.now();
    }

    public List<StockCountItem> getItems() {
        return Collections.unmodifiableList(this.items);
    }

    public boolean isInProgress() {
        return this.status == StockCountStatus.IN_PROGRESS;
    }

    private void requireInProgress() {
        if (!isInProgress()) {
            throw new InvalidStockCountStateException(this.status);
        }
    }
}
