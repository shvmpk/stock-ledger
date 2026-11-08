package com.shvmpk.stock_ledger.common.error;

import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

public class RetiredProductException extends DomainException {

    public RetiredProductException(String sku, String action) {
        super(
                HttpStatus.CONFLICT,
                "PRODUCT_RETIRED",
                String.format("SKU '%s' is retired and cannot be %s.", sku, action),
                Map.of(
                        "sku", sku,
                        "actions", List.of(StockAction.VIEW_RETIRED_PRODUCTS)
                )
        );
    }
}
