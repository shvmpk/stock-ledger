package com.shvmpk.stock_ledger.common.error;

import org.springframework.http.HttpStatus;

public class OrderNumberConflictException extends DomainException {

    public OrderNumberConflictException(String orderNumber) {
        super(
                HttpStatus.CONFLICT,
                "ORDER_NUMBER_CONFLICT",
                String.format("Order number '%s' already belongs to a different order.", orderNumber)
        );
    }
}
