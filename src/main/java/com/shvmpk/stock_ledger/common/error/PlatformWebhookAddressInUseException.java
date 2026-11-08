package com.shvmpk.stock_ledger.common.error;

import com.shvmpk.stock_ledger.platform.entity.PlatformType;
import org.springframework.http.HttpStatus;

/**
 * Raised when activating a connection would give two active connections the same webhook
 * address. The address is the identity of a connection, so it must stay unique among the
 * connections that are currently receiving orders.
 */
public class PlatformWebhookAddressInUseException extends DomainException {

    public PlatformWebhookAddressInUseException(String slug) {
        super(
                HttpStatus.CONFLICT,
                "PLATFORM_WEBHOOK_ADDRESS_IN_USE",
                String.format(
                        "Another active connection already receives orders at %s%s. "
                                + "Pause that connection first.",
                        PlatformType.WEBHOOK_ROOT,
                        slug
                )
        );
    }
}
