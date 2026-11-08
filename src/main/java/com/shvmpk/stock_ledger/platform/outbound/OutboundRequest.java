package com.shvmpk.stock_ledger.platform.outbound;

/**
 * A request built by a platform adapter for the hardened outbound path. The
 * adapter decides the address, headers, and timeout; the client decides
 * whether it is safe to send.
 */
public record OutboundRequest(
        String method,
        java.net.URI uri,
        java.time.Duration timeout,
        java.util.Map<String, java.util.List<String>> headers
) {
    public OutboundRequest {
        headers = headers == null ? java.util.Map.of() : java.util.Map.copyOf(headers);
    }

    public static OutboundRequest get(
            java.net.URI uri,
            java.time.Duration timeout,
            java.util.Map<String, String> headers
    ) {
        java.util.Map<String, java.util.List<String>> copy = new java.util.LinkedHashMap<>();
        if (headers != null) {
            headers.forEach((name, value) -> copy.put(name, java.util.List.of(value)));
        }
        return new OutboundRequest("GET", uri, timeout, copy);
    }

    public java.util.Optional<String> firstHeader(String name) {
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .flatMap(entry -> entry.getValue().stream())
                .findFirst();
    }
}
