package com.shvmpk.stock_ledger.common.error;

import org.springframework.http.HttpStatus;

/**
 * A pause must say why. Without a reason, the card and the history cannot
 * explain why orders stopped arriving.
 */
public class PauseReasonRequiredException extends DomainException {

    public PauseReasonRequiredException() {
        super(
                HttpStatus.BAD_REQUEST,
                "PAUSE_REASON_REQUIRED",
                "Give a reason for pausing this connection. It is recorded in the connection history."
        );
    }
}
