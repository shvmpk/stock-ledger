package com.shvmpk.stock_ledger.common.error;

import org.springframework.http.HttpStatus;

public class DuplicateStockCountItemException extends DomainException {

    public DuplicateStockCountItemException(String sku) {
        super(
                HttpStatus.BAD_REQUEST,
                "DUPLICATE_STOCK_COUNT_ITEM",
                String.format(
                        "Variant '%s' has already been recorded in this stock count session.",
                        sku
                )
        );
    }
}
