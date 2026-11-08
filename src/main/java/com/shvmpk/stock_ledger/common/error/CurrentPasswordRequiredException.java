package com.shvmpk.stock_ledger.common.error;

import org.springframework.http.HttpStatus;

public class CurrentPasswordRequiredException extends DomainException {

    public CurrentPasswordRequiredException() {
        super(
                HttpStatus.BAD_REQUEST,
                "CURRENT_PASSWORD_REQUIRED",
                "Please confirm your current password before choosing a new one."
        );
    }
}
