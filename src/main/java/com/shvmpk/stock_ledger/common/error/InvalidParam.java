package com.shvmpk.stock_ledger.common.error;

public record InvalidParam(
        String field,
        Object rejectedValue,
        String message
) {
}
