package com.shvmpk.stock_ledger.common.error;

import org.springframework.http.HttpStatus;

public class InvalidCurrentPasswordException extends DomainException {

    public InvalidCurrentPasswordException() {
        super(
                HttpStatus.BAD_REQUEST,
                "INVALID_CURRENT_PASSWORD",
                "That password doesn't match your current one."
        );
    }
}
