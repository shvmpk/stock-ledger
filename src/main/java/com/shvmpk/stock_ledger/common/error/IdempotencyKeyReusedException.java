package com.shvmpk.stock_ledger.common.error;

import org.springframework.http.HttpStatus;

public class IdempotencyKeyReusedException extends DomainException {

    public IdempotencyKeyReusedException() {
        super(
                HttpStatus.CONFLICT,
                "IDEMPOTENCY_KEY_REUSED",
                "The idempotency key was already used with a different request payload."
        );
    }
}
