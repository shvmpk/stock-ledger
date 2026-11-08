package com.shvmpk.stock_ledger.common.error;

import org.springframework.http.HttpStatus;

public class InsufficientReservedStockException extends DomainException {

    public InsufficientReservedStockException(String sku, int requestedQuantity) {
        this("ship", sku, requestedQuantity);
    }

    public InsufficientReservedStockException(String action, String sku, int requestedQuantity) {
        super(
                HttpStatus.CONFLICT,
                "INSUFFICIENT_RESERVED_STOCK",
                String.format(
                        "Cannot %s %d units of SKU '%s' because the reserved stock is insufficient.",
                        action,
                        requestedQuantity,
                        sku
                )
        );
    }

    public InsufficientReservedStockException(String action, StockShortageDetails details) {
        super(
                HttpStatus.CONFLICT,
                "INSUFFICIENT_RESERVED_STOCK",
                String.format(
                        "Cannot %s %d units of SKU '%s'. Only %d are reserved for open orders, while %d are on the shelf. "
                                + "Check the order's reservation or record a stock count.",
                        action,
                        details.requestedQuantity(),
                        details.sku(),
                        details.reservedQuantity(),
                        details.onHandQuantity()
                ),
                details.asExtensions()
        );
    }
}
