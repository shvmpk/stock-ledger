package com.shvmpk.stock_ledger.common.error;

import org.springframework.http.HttpStatus;

/**
 * Raised when an edit arrives without the version the client read. Refusing it
 * is the only way to keep the "changed by someone else" guarantee: a save with
 * no version cannot tell whether it is overwriting newer work.
 */
public class InvalidPlatformVersionException extends DomainException {

    public InvalidPlatformVersionException() {
        super(
                HttpStatus.BAD_REQUEST,
                "INVALID_PLATFORM_VERSION",
                "A version number is required to save changes to a platform connection."
        );
    }
}
