package com.shvmpk.stock_ledger.common.error;

import org.springframework.http.HttpStatus;

public class InvalidPlatformEndpointException extends DomainException {

    public InvalidPlatformEndpointException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_PLATFORM_ENDPOINT", message);
    }
}
