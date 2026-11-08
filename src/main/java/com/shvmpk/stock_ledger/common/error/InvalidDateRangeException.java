package com.shvmpk.stock_ledger.common.error;

import org.springframework.http.HttpStatus;

public class InvalidDateRangeException extends DomainException {

    public InvalidDateRangeException() {
        super(
                HttpStatus.BAD_REQUEST,
                "INVALID_DATE_RANGE",
                "The 'from' timestamp must be earlier than or equal to the 'to' timestamp."
        );
    }
}
