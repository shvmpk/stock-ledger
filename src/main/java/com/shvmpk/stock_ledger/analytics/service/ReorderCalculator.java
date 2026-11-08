package com.shvmpk.stock_ledger.analytics.service;

import com.shvmpk.stock_ledger.analytics.dto.ReorderHistoryStatus;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * Pure reorder arithmetic. It is deliberately separated from the database so
 * the honesty rules can be tested without a Spring context: no estimate is
 * produced until there is enough real sales history, and every suggestion is
 * visibly derived from the sales window, lead time, and target cover.
 */
@Component
public class ReorderCalculator {

    public static final int SALES_WINDOW_DAYS = 90;
    public static final int MINIMUM_HISTORY_DAYS = 21;
    public static final int MINIMUM_UNITS_SOLD = 5;
    public static final int TARGET_COVER_DAYS = 14;

    public ReorderEstimate estimate(
            int available,
            int unitsSold,
            OffsetDateTime firstShippedAt,
            int leadTimeDays,
            OffsetDateTime now
    ) {
        boolean enoughHistory = firstShippedAt != null
                && !firstShippedAt.isAfter(now.minusDays(MINIMUM_HISTORY_DAYS))
                && unitsSold >= MINIMUM_UNITS_SOLD;

        if (!enoughHistory) {
            return new ReorderEstimate(ReorderHistoryStatus.INSUFFICIENT, null, null);
        }

        double dailyVelocity = unitsSold / (double) SALES_WINDOW_DAYS;
        Integer daysOfCover = available <= 0
                ? 0
                : (int) Math.floor(available / dailyVelocity);
        int targetUnits = (int) Math.ceil(dailyVelocity * (leadTimeDays + TARGET_COVER_DAYS));
        int suggestedQuantity = Math.max(0, targetUnits - available);

        return new ReorderEstimate(
                ReorderHistoryStatus.SUFFICIENT,
                daysOfCover,
                suggestedQuantity
        );
    }

    public boolean needsAction(ReorderEstimate estimate, int available, Integer reorderLevel) {
        if (available <= 0) {
            return true;
        }
        if (reorderLevel != null && available <= reorderLevel) {
            return true;
        }
        return estimate.suggestedQuantity() != null && estimate.suggestedQuantity() > 0;
    }
}
