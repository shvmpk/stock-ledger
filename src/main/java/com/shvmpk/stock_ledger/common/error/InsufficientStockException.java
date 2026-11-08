package com.shvmpk.stock_ledger.common.error;

import org.springframework.http.HttpStatus;

import java.util.stream.Collectors;

public class InsufficientStockException extends DomainException {

    public InsufficientStockException(String sku, int requestedQuantity, int availableQuantity) {
        super(
                HttpStatus.CONFLICT,
                "INSUFFICIENT_STOCK",
                String.format("Cannot reserve %d units of SKU '%s'. Only %d available.", requestedQuantity, sku, availableQuantity)
        );
    }

    public InsufficientStockException(String message) {
        super(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", message);
    }

    public InsufficientStockException(StockShortageDetails details) {
        super(
                HttpStatus.CONFLICT,
                "INSUFFICIENT_STOCK",
                explain(details),
                details.asExtensions()
        );
    }

    private static String explain(StockShortageDetails details) {
        StringBuilder message = new StringBuilder(String.format(
                "You asked for %d units of SKU '%s'. %d are on the shelf and %d are reserved for open orders, leaving %d available.",
                details.requestedQuantity(),
                details.sku(),
                details.onHandQuantity(),
                details.reservedQuantity(),
                details.availableQuantity()
        ));
        if (!details.reservedBy().isEmpty()) {
            message.append(" Reserved by: ");
            message.append(details.reservedBy().stream()
                    .map(holder -> holder.orderNumber() + " (" + holder.reservedQuantity() + ")")
                    .collect(Collectors.joining(", ")));
            message.append('.');
        }
        message.append(" Receive more stock, or reduce this order.");
        return message.toString();
    }
}
