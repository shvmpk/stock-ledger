package com.shvmpk.stock_ledger.platform.service;

public class PlatformSyncException extends RuntimeException {

    public PlatformSyncException(String message) {
        super(message);
    }

    public PlatformSyncException(String message, Throwable cause) {
        super(message, cause);
    }
}
