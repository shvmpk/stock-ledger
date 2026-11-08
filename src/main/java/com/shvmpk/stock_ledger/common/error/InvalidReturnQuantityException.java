package com.shvmpk.stock_ledger.common.error;

import org.springframework.http.HttpStatus;

public class InvalidReturnQuantityException extends DomainException {

    public InvalidReturnQuantityException(String sku, int requestedQuantity, int remainingQuantity) {
        super(
                HttpStatus.BAD_REQUEST,
                "INVALID_RETURN_QUANTITY",
                String.format(
                        "Cannot return %d units of SKU '%s'. Only %d remaining unreturned units are available.",
                        requestedQuantity,
                        sku,
                        remainingQuantity
                )
        );
    }
}
