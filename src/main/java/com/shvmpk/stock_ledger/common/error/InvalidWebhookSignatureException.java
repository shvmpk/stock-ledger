package com.shvmpk.stock_ledger.common.error;

import org.springframework.http.HttpStatus;

public class InvalidWebhookSignatureException extends DomainException {

    public InvalidWebhookSignatureException(String platform) {
        super(
                HttpStatus.UNAUTHORIZED,
                "INVALID_WEBHOOK_SIGNATURE",
                String.format("HMAC signature validation failed for platform '%s'.", platform)
        );
    }
}
