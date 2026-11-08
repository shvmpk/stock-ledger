package com.shvmpk.stock_ledger.platform.adapter;

import com.shvmpk.stock_ledger.platform.entity.PlatformCredentials;
import com.shvmpk.stock_ledger.platform.entity.PlatformType;
import com.shvmpk.stock_ledger.platform.outbound.OutboundRequest;
import com.shvmpk.stock_ledger.platform.outbound.OutboundResponse;
import com.shvmpk.stock_ledger.platform.service.PlatformSyncTarget;

import java.time.Duration;

public interface PlatformOrderAdapter {

    PlatformType platformType();

    OutboundRequest buildPageRequest(
            PlatformSyncTarget target,
            PlatformCredentials credentials,
            SyncWindow window,
            String pageToken,
            Duration timeout
    );

    /**
     * A single lightweight authenticated call that proves the address and
     * credential work, without pulling a page of orders.
     */
    OutboundRequest buildTestRequest(
            PlatformSyncTarget target,
            PlatformCredentials credentials,
            Duration timeout
    );

    PlatformOrderPage parsePage(OutboundResponse response, String pageToken);
}
