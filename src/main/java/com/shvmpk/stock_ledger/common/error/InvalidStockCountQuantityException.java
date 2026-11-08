package com.shvmpk.stock_ledger.common.error;

import org.springframework.http.HttpStatus;

public class InvalidStockCountQuantityException extends DomainException {

    public InvalidStockCountQuantityException() {
        super(
                HttpStatus.BAD_REQUEST,
                "INVALID_STOCK_COUNT_QUANTITY",
                "Counted on-hand quantity cannot be negative."
        );
    }
}
