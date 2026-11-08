package com.shvmpk.stock_ledger.platform.entity;

/**
 * The plain-language result of testing one connection. It is shown on the
 * connection card and stored on the history entry.
 */
public enum PlatformTestOutcome {
    SUCCESS,
    AUTHENTICATION_FAILED,
    FAILED,
    UNREACHABLE,
    AWAITING_INBOUND,
    NOT_CONFIGURED
}
