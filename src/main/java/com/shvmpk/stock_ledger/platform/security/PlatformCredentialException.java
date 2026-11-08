package com.shvmpk.stock_ledger.platform.security;

import com.shvmpk.stock_ledger.common.error.DomainException;
import org.springframework.http.HttpStatus;

public class PlatformCredentialException extends DomainException {

    public PlatformCredentialException(String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "PLATFORM_CREDENTIAL_ERROR", message);
    }
}
