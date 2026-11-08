package com.shvmpk.stock_ledger.settings.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "reorder_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReorderSettings {

    public static final short SINGLETON_ID = 1;

    @Id
    @Column(nullable = false, updatable = false)
    private Short id;

    @Column(name = "lead_time_days", nullable = false)
    private int leadTimeDays;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public void updateLeadTime(int leadTimeDays, UUID actorId) {
        if (leadTimeDays < 1 || leadTimeDays > 365) {
            throw new IllegalArgumentException("Supplier lead time must be between 1 and 365 days");
        }
        this.leadTimeDays = leadTimeDays;
        this.updatedBy = actorId;
        this.updatedAt = OffsetDateTime.now();
    }
}
