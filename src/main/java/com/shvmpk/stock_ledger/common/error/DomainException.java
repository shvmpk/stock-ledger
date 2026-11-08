package com.shvmpk.stock_ledger.common.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
public abstract class DomainException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final String errorCode;
    private final Map<String, Object> extensions;

    protected DomainException(HttpStatus httpStatus, String errorCode, String message) {
        this(httpStatus, errorCode, message, Map.of());
    }

    protected DomainException(
            HttpStatus httpStatus,
            String errorCode,
            String message,
            Map<String, Object> extensions
    ) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.extensions = Collections.unmodifiableMap(new LinkedHashMap<>(extensions));
    }
}
