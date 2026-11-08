package com.shvmpk.stock_ledger.config.security;

import java.net.InetAddress;

public record HttpRequestMetadata(
        InetAddress ipAddress,
        String userAgent,
        String requestId
) {
    public static HttpRequestMetadata empty() {
        return new HttpRequestMetadata(null, null, null);
    }
}
