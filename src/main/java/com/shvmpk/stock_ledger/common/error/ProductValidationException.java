package com.shvmpk.stock_ledger.common.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.List;

@Getter
public class ProductValidationException extends DomainException {

    private final List<InvalidParam> invalidParams;

    public ProductValidationException(List<InvalidParam> invalidParams) {
        super(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "One or more product fields are invalid."
        );
        this.invalidParams = List.copyOf(invalidParams);
    }

    public ProductValidationException(String field, String message) {
        this(List.of(new InvalidParam(field, null, message)));
    }
}
