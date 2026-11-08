package com.shvmpk.stock_ledger.platform.outbound;

/**
 * A hostname resolved to an address the application must never call, such as a
 * private network, loopback, link-local, or multicast address.
 */
public class BlockedOutboundAddressException extends PlatformOutboundException {

    public BlockedOutboundAddressException(String message) {
        super(message);
    }
}
