package com.shvmpk.stock_ledger.common.error;

import org.springframework.http.HttpStatus;

public class InvalidOrderStateTransitionException extends DomainException {

    public InvalidOrderStateTransitionException(String currentStatus, String targetStatus) {
        super(
                HttpStatus.BAD_REQUEST,
                "INVALID_STATE_TRANSITION",
                String.format("Cannot transition order from status '%s' to '%s'.", currentStatus, targetStatus)
        );
    }
}
