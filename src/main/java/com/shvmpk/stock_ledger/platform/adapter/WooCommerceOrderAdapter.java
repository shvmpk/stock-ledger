package com.shvmpk.stock_ledger.platform.adapter;

import com.shvmpk.stock_ledger.platform.entity.PlatformCredentials;
import com.shvmpk.stock_ledger.platform.entity.PlatformType;
import com.shvmpk.stock_ledger.platform.outbound.OutboundRequest;
import com.shvmpk.stock_ledger.platform.outbound.OutboundResponse;
import com.shvmpk.stock_ledger.platform.service.PlatformSyncException;
import com.shvmpk.stock_ledger.platform.service.PlatformSyncTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class WooCommerceOrderAdapter implements PlatformOrderAdapter {

    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 20;

    private final ObjectMapper objectMapper;

    @Override
    public PlatformType platformType() {
        return PlatformType.WOOCOMMERCE;
    }

    @Override
    public OutboundRequest buildPageRequest(
            PlatformSyncTarget target,
            PlatformCredentials credentials,
            SyncWindow window,
            String pageToken,
            Duration timeout
    ) {
        String authorization = authorization(credentials);

        int page = parsePageToken(pageToken);
        URI uri = UriComponentsBuilder.fromUriString(ordersEndpoint(target.apiEndpoint()))
                .queryParam("per_page", PAGE_SIZE)
                .queryParam("page", page)
                .queryParam("orderby", "date")
                .queryParam("order", "asc")
                .queryParam("after", window.from().toString())
                .queryParam("before", window.to().toString())
                .build()
                .encode()
                .toUri();

        return OutboundRequest.get(uri, timeoutOrDefault(timeout), Map.of(
                "Accept", "application/json",
                "Authorization", authorization
        ));
    }

    @Override
    public OutboundRequest buildTestRequest(
            PlatformSyncTarget target,
            PlatformCredentials credentials,
            Duration timeout
    ) {
        String authorization = authorization(credentials);

        URI uri = UriComponentsBuilder.fromUriString(productsEndpoint(target.apiEndpoint()))
                .queryParam("per_page", 1)
                .build()
                .encode()
                .toUri();

        return OutboundRequest.get(uri, timeoutOrDefault(timeout), Map.of(
                "Accept", "application/json",
                "Authorization", authorization
        ));
    }

    @Override
    public PlatformOrderPage parsePage(OutboundResponse response, String pageToken) {
        JsonNode root;
        try {
            root = objectMapper.readTree(response.body());
        } catch (Exception ex) {
            throw new PlatformSyncException("Malformed response from WOOCOMMERCE.", ex);
        }

        if (root == null || !root.isArray()) {
            throw new PlatformSyncException("Malformed response from WOOCOMMERCE.");
        }

        List<ExternalOrder> orders = new ArrayList<>();
        for (JsonNode orderNode : root) {
            orders.add(mapOrder(orderNode));
        }

        return new PlatformOrderPage(orders, nextPageToken(response, orders.size(), parsePageToken(pageToken)));
    }

    private String authorization(PlatformCredentials credentials) {
        String consumerKey = credentials == null ? null : credentials.consumerKey();
        String consumerSecret = credentials == null ? null : credentials.consumerSecret();
        if (consumerKey == null || consumerKey.isBlank()
                || consumerSecret == null || consumerSecret.isBlank()) {
            throw new PlatformSyncException("WooCommerce consumer key and secret are not configured.");
        }

        return "Basic " + Base64.getEncoder().encodeToString(
                (consumerKey + ":" + consumerSecret).getBytes(StandardCharsets.UTF_8)
        );
    }

    private Duration timeoutOrDefault(Duration timeout) {
        return Objects.requireNonNullElse(timeout, Duration.ofSeconds(10));
    }

    private int parsePageToken(String pageToken) {
        if (pageToken == null || pageToken.isBlank()) {
            return 1;
        }
        try {
            int page = Integer.parseInt(pageToken);
            if (page < 1) {
                throw new NumberFormatException("Page must be positive");
            }
            return page;
        } catch (NumberFormatException ex) {
            throw new PlatformSyncException("Invalid WooCommerce page token.", ex);
        }
    }

    private String nextPageToken(OutboundResponse response, int orderCount, int currentPage) {
        if (currentPage >= MAX_PAGES) {
            return null;
        }

        Optional<String> totalPagesHeader = response.firstHeader("X-WP-TotalPages");
        if (totalPagesHeader.isPresent()) {
            try {
                int totalPages = Integer.parseInt(totalPagesHeader.get());
                if (currentPage < totalPages) {
                    return String.valueOf(currentPage + 1);
                }
                return null;
            } catch (NumberFormatException ignored) {
                // Fall through to the page-size heuristic for malformed headers.
            }
        }

        return orderCount >= PAGE_SIZE ? String.valueOf(currentPage + 1) : null;
    }

    private ExternalOrder mapOrder(JsonNode orderNode) {
        String externalOrderId = text(orderNode, "id");
        String rawNumber = text(orderNode, "number");
        String number = hasText(rawNumber) ? stripLeadingHash(rawNumber) : externalOrderId;
        String orderNumber = "ORD-WOO-" + number;

        JsonNode billing = orderNode.path("billing");
        JsonNode shipping = orderNode.path("shipping");
        JsonNode customer = hasText(text(shipping, "first_name")) ? shipping : billing;

        String customerName = customerName(
                text(customer, "first_name"),
                text(customer, "last_name")
        );
        String customerEmail = text(billing, "email");

        JsonNode addressNode = hasText(text(shipping, "address_1")) ? shipping : billing;
        String address = formatAddress(addressNode);

        String currency = text(orderNode, "currency");

        List<ExternalOrder.ExternalOrderItem> items = new ArrayList<>();
        JsonNode itemsNode = orderNode.path("line_items");
        if (itemsNode.isArray()) {
            for (JsonNode itemNode : itemsNode) {
                items.add(new ExternalOrder.ExternalOrderItem(
                        text(itemNode, "sku"),
                        itemNode.path("quantity").asInt(1),
                        decimal(text(itemNode, "price"))
                ));
            }
        }

        return new ExternalOrder(
                externalOrderId,
                orderNumber,
                customerName,
                customerEmail,
                address,
                decimal(text(orderNode, "shipping_total")),
                currency,
                items
        );
    }

    private String formatAddress(JsonNode address) {
        String line1 = text(address, "address_1");
        String line2 = text(address, "address_2");
        String zip = text(address, "postcode");
        String city = text(address, "city");
        String country = text(address, "country");

        String street = hasText(line1) && hasText(line2)
                ? line1 + ", " + line2
                : hasText(line1) ? line1 : line2;

        List<String> parts = new ArrayList<>();
        if (hasText(street)) {
            parts.add(street);
        }
        String cityLine = ((hasText(zip) ? zip + " " : "") + city).trim();
        if (hasText(cityLine)) {
            parts.add(cityLine);
        }
        if (hasText(country)) {
            parts.add(country);
        }
        return String.join(", ", parts);
    }

    private String customerName(String firstName, String lastName) {
        String name = (firstName + " " + lastName).trim();
        return name.isEmpty() ? "Platform Customer" : name;
    }

    private String ordersEndpoint(String apiEndpoint) {
        String base = apiEndpoint.endsWith("/")
                ? apiEndpoint.substring(0, apiEndpoint.length() - 1)
                : apiEndpoint;
        return base.endsWith("/orders") ? base : base + "/orders";
    }

    private String productsEndpoint(String apiEndpoint) {
        String base = apiEndpoint.endsWith("/")
                ? apiEndpoint.substring(0, apiEndpoint.length() - 1)
                : apiEndpoint;
        if (base.endsWith("/products")) {
            return base;
        }
        if (base.endsWith("/orders")) {
            return base.substring(0, base.length() - "/orders".length()) + "/products";
        }
        return base + "/products";
    }

    private String text(JsonNode node, String field) {
        if (node == null) {
            return "";
        }
        JsonNode value = node.path(field);
        return value == null || value.isNull() ? "" : value.asString("");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String stripLeadingHash(String value) {
        return value.startsWith("#") ? value.substring(1) : value;
    }

    private BigDecimal decimal(String value) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }
}
