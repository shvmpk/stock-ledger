package com.shvmpk.stock_ledger.platform.dto;

import com.shvmpk.stock_ledger.platform.entity.PlatformTestOutcome;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * What the owner sees after testing one connection. The message is plain
 * language and safe to display; it never contains a credential or a raw
 * platform response body.
 */
public record PlatformConnectionTestResponse(
        UUID connectionId,
        PlatformTestOutcome outcome,
        Integer httpStatus,
        String message,
        OffsetDateTime testedAt,
        OffsetDateTime lastInboundOrderAt
) {
}
