package com.shvmpk.stock_ledger.common.error;

import org.springframework.http.HttpStatus;

/**
 * Raised when a save is built on a version that is no longer current because
 * someone else saved the same record first. The caller must reload the record
 * and apply the change again; the server never silently keeps the later save.
 */
public class ConcurrentChangeException extends DomainException {

    public ConcurrentChangeException() {
        super(
                HttpStatus.CONFLICT,
                "CONCURRENT_CHANGE",
                "This record was changed by someone else after you opened it. "
                        + "Reload it and apply your change again."
        );
    }
}
