package com.shvmpk.stock_ledger.common.error;

import org.springframework.http.HttpStatus;

public class UnsupportedPlatformException extends DomainException {

    public UnsupportedPlatformException(String platform) {
        super(
                HttpStatus.BAD_REQUEST,
                "UNSUPPORTED_PLATFORM",
                String.format("Platform '%s' is not supported.", platform)
        );
    }
}
