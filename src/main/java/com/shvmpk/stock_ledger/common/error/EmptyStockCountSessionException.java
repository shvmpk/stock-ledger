package com.shvmpk.stock_ledger.common.error;

import org.springframework.http.HttpStatus;

public class EmptyStockCountSessionException extends DomainException {

    public EmptyStockCountSessionException() {
        super(
                HttpStatus.BAD_REQUEST,
                "EMPTY_STOCK_COUNT_SESSION",
                "Cannot complete a stock count session without any counted items."
        );
    }
}
