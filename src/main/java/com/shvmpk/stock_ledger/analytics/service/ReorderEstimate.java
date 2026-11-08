package com.shvmpk.stock_ledger.analytics.service;

import com.shvmpk.stock_ledger.analytics.dto.ReorderHistoryStatus;

public record ReorderEstimate(
        ReorderHistoryStatus historyStatus,
        Integer daysOfCover,
        Integer suggestedQuantity
) {
}
