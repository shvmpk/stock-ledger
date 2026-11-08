package com.shvmpk.stock_ledger.common.api;

import java.time.Instant;

public record ResponseMeta(
        Instant timestamp,
        String requestId
) {
    public static ResponseMeta of(String requestId) {
        return new ResponseMeta(Instant.now(), requestId);
    }

    public static ResponseMeta now() {
        return new ResponseMeta(Instant.now(), null);
    }
}
