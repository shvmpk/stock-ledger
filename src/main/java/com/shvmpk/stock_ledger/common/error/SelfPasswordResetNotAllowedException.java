package com.shvmpk.stock_ledger.common.error;

import org.springframework.http.HttpStatus;

public class SelfPasswordResetNotAllowedException extends DomainException {

    public SelfPasswordResetNotAllowedException() {
        super(
                HttpStatus.BAD_REQUEST,
                "SELF_PASSWORD_RESET_NOT_ALLOWED",
                "Use the account menu to change your own password."
        );
    }
}
