package com.shvmpk.stock_ledger.platform.adapter;

import java.time.OffsetDateTime;

public record SyncWindow(
        OffsetDateTime from,
        OffsetDateTime to
) {
    public static SyncWindow forConnection(OffsetDateTime previousLastSyncAt, OffsetDateTime now) {
        OffsetDateTime from = previousLastSyncAt != null
                ? previousLastSyncAt.minusMinutes(5)
                : now.minusDays(30);
        return new SyncWindow(from, now);
    }
}
