package com.shvmpk.stock_ledger.common.error;

import com.shvmpk.stock_ledger.platform.entity.PlatformType;
import org.springframework.http.HttpStatus;

public class UnsupportedPlatformTypeException extends DomainException {

    public UnsupportedPlatformTypeException(PlatformType platformType) {
        super(
                HttpStatus.BAD_REQUEST,
                "UNSUPPORTED_PLATFORM_TYPE",
                String.format("Platform type '%s' does not support connection management.", platformType)
        );
    }
}
