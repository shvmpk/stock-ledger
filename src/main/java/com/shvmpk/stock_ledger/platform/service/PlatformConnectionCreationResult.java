package com.shvmpk.stock_ledger.platform.service;

import com.shvmpk.stock_ledger.platform.dto.PlatformConnectionResponse;

/**
 * The outcome of a create request. {@code replayed} is true when the same
 * request key had already created a connection, so the caller can answer with
 * the original resource instead of a second creation.
 */
public record PlatformConnectionCreationResult(
        PlatformConnectionResponse connection,
        boolean replayed
) {
}
