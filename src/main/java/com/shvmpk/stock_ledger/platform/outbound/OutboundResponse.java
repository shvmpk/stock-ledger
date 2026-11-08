package com.shvmpk.stock_ledger.platform.outbound;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record OutboundResponse(
        int statusCode,
        Map<String, List<String>> headers,
        String body
) {
    public OutboundResponse {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        body = body == null ? "" : body;
    }

    public Optional<String> firstHeader(String name) {
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .flatMap(entry -> entry.getValue().stream())
                .findFirst();
    }
}
