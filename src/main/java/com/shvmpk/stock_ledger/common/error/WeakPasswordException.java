package com.shvmpk.stock_ledger.common.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.List;

@Getter
public class WeakPasswordException extends DomainException {

    private final String field;
    private final List<String> violations;

    public WeakPasswordException(String field, List<String> violations) {
        super(
                HttpStatus.BAD_REQUEST,
                "WEAK_PASSWORD",
                "The password does not meet the security policy."
        );
        this.field = field;
        this.violations = List.copyOf(violations);
    }
}
