package com.shvmpk.stock_ledger.common.error;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends DomainException {

    public ResourceNotFoundException(String resourceType, Object identifier) {
        super(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                String.format("%s with identifier '%s' was not found.", resourceType, identifier)
        );
    }

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", message);
    }
}
