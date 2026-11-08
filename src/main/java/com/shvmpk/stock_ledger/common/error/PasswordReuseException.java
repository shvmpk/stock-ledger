package com.shvmpk.stock_ledger.common.error;

import org.springframework.http.HttpStatus;

public class PasswordReuseException extends DomainException {

    public PasswordReuseException() {
        super(
                HttpStatus.BAD_REQUEST,
                "PASSWORD_REUSE",
                "The new password must be different from your current password."
        );
    }
}
