package com.shvmpk.stock_ledger.common.error;

import org.springframework.http.HttpStatus;

public class NoPlatformChangesException extends DomainException {

    public NoPlatformChangesException() {
        super(
                HttpStatus.BAD_REQUEST,
                "NO_PLATFORM_CHANGES",
                "At least one platform connection field must be provided."
        );
    }
}
