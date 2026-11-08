package com.shvmpk.stock_ledger.platform.adapter;

import java.util.List;

public record PlatformOrderPage(
        List<ExternalOrder> orders,
        String nextPageToken
) {
}
