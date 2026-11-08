package com.shvmpk.stock_ledger.platform.outbound;

/**
 * A platform could not be contacted safely or did not answer in the expected
 * way. The message is safe to show to an owner: it never contains a token,
 * secret, or raw response body.
 */
public class PlatformOutboundException extends RuntimeException {

    public PlatformOutboundException(String message) {
        super(message);
    }

    public PlatformOutboundException(String message, Throwable cause) {
        super(message, cause);
    }
}
