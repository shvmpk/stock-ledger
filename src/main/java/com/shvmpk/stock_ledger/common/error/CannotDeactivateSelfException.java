package com.shvmpk.stock_ledger.common.error;

import org.springframework.http.HttpStatus;

public class CannotDeactivateSelfException extends DomainException {

    public CannotDeactivateSelfException() {
        super(
                HttpStatus.BAD_REQUEST,
                "CANNOT_DEACTIVATE_SELF",
                "You cannot deactivate your own account."
        );
    }
}
