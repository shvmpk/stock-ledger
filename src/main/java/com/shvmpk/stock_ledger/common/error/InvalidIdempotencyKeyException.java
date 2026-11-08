package com.shvmpk.stock_ledger.common.error;

import org.springframework.http.HttpStatus;

/**
 * Raised when a create carries a request key that is not a stable, opaque
 * identifier. A malformed key is refused rather than ignored, because ignoring
 * it would silently remove the duplicate-submit protection.
 */
public class InvalidIdempotencyKeyException extends DomainException {

    public InvalidIdempotencyKeyException() {
        super(
                HttpStatus.BAD_REQUEST,
                "INVALID_IDEMPOTENCY_KEY",
                "An idempotency key must be 8 to 100 characters using letters, "
                        + "digits, hyphens, or underscores."
        );
    }
}
