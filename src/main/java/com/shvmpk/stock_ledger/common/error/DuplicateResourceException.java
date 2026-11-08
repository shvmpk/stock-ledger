package com.shvmpk.stock_ledger.common.error;

import org.springframework.http.HttpStatus;

public class DuplicateResourceException extends DomainException {

    public DuplicateResourceException(String resourceType, String field, Object value) {
        super(
                HttpStatus.CONFLICT,
                "DUPLICATE_RESOURCE",
                String.format("%s with %s '%s' already exists.", resourceType, field, value)
        );
    }
}
