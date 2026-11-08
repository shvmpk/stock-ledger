package com.shvmpk.stock_ledger.common.error;

import org.springframework.http.HttpStatus;

public class NoUserChangesException extends DomainException {

    public NoUserChangesException() {
        super(
                HttpStatus.BAD_REQUEST,
                "NO_USER_CHANGES",
                "At least one of fullName, role, or active must be provided."
        );
    }
}
