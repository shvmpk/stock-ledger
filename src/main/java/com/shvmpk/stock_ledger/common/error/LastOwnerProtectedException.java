package com.shvmpk.stock_ledger.common.error;

import org.springframework.http.HttpStatus;

public class LastOwnerProtectedException extends DomainException {

    public LastOwnerProtectedException() {
        super(
                HttpStatus.CONFLICT,
                "LAST_OWNER_PROTECTED",
                "Cannot deactivate or demote the last active OWNER account."
        );
    }
}
