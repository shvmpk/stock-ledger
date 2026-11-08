package com.shvmpk.stock_ledger.common.error;

import org.springframework.http.HttpStatus;

public class InvalidPlatformCredentialsException extends DomainException {

    public InvalidPlatformCredentialsException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_PLATFORM_CREDENTIALS", message);
    }
}
