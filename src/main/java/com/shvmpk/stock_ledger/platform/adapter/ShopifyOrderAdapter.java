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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class ShopifyOrderAdapter implements PlatformOrderAdapter {

    private static final Pattern NEXT_LINK = Pattern.compile("<([^>]+)>;\\s*rel=\"?next\"?");
    private static final int PAGE_SIZE = 250;

    private final ObjectMapper objectMapper;

    @Override
    public PlatformType platformType() {
        return PlatformType.SHOPIFY;
    }

    @Override
    public OutboundRequest buildPageRequest(
            PlatformSyncTarget target,
            PlatformCredentials credentials,
            SyncWindow window,
            String pageToken,
            Duration timeout
    ) {
        String accessToken = requireAccessToken(credentials);

        URI uri = pageToken != null
                ? validatedNextUri(target, pageToken)
                : firstPageUri(target, window);

        return OutboundRequest.get(uri, timeoutOrDefault(timeout), Map.of(
                "Accept", "application/json",
                "X-Shopify-Access-Token", accessToken
        ));
    }

    @Override
    public OutboundRequest buildTestRequest(
            PlatformSyncTarget target,
            PlatformCredentials credentials,
            Duration timeout
    ) {
        String accessToken = requireAccessToken(credentials);
        String endpoint = stripTrailingSlash(target.apiEndpoint());
        String shopUrl = endpoint.endsWith("/shop.json") ? endpoint : endpoint + "/shop.json";

        return OutboundRequest.get(URI.create(shopUrl), timeoutOrDefault(timeout), Map.of(
                "Accept", "application/json",
                "X-Shopify-Access-Token", accessToken
        ));
    }

    @Override
    public PlatformOrderPage parsePage(OutboundResponse response, String pageToken) {
        JsonNode root;
        try {
            root = objectMapper.readTree(response.body());
        } catch (Exception ex) {
            throw new PlatformSyncException("Malformed response from SHOPIFY.", ex);
        }

        if (root == null || !root.isObject()) {
            throw new PlatformSyncException("Malformed response from SHOPIFY.");
        }

        JsonNode ordersNode = root.get("orders");
        if (ordersNode == null || ordersNode.isNull()) {
            return new PlatformOrderPage(List.of(), nextPageToken(response));
        }
        if (!ordersNode.isArray()) {
            throw new PlatformSyncException("Malformed response from SHOPIFY.");
        }

        List<ExternalOrder> orders = new ArrayList<>();
        for (JsonNode orderNode : ordersNode) {
            orders.add(mapOrder(orderNode));
        }
        return new PlatformOrderPage(orders, nextPageToken(response));
    }

    private String requireAccessToken(PlatformCredentials credentials) {
        String accessToken = credentials == null ? null : credentials.accessToken();
        if (accessToken == null || accessToken.isBlank()) {
            throw new PlatformSyncException("Shopify access token is not configured.");
        }
        return accessToken;
    }

    private Duration timeoutOrDefault(Duration timeout) {
        return Objects.requireNonNullElse(timeout, Duration.ofSeconds(10));
    }

    private URI firstPageUri(PlatformSyncTarget target, SyncWindow window) {
        String endpoint = stripTrailingSlash(target.apiEndpoint());
        String ordersUrl = endpoint.endsWith("/orders.json") ? endpoint : endpoint + "/orders.json";
        return UriComponentsBuilder.fromUriString(ordersUrl)
                .queryParam("limit", PAGE_SIZE)
                .queryParam("status", "any")
                .queryParam("created_at_min", window.from().toString())
                .queryParam("created_at_max", window.to().toString())
                .build()
                .encode()
                .toUri();
    }

    private URI validatedNextUri(PlatformSyncTarget target, String pageToken) {
        URI next;
        try {
            next = URI.create(pageToken);
        } catch (IllegalArgumentException ex) {
            throw new PlatformSyncException("Shopify pagination returned an invalid link.", ex);
        }

        URI base = URI.create(target.apiEndpoint());
        if (next.getHost() == null || !next.getHost().equalsIgnoreCase(base.getHost())) {
            throw new PlatformSyncException("Shopify pagination returned an unexpected host.");
        }
        return next;
    }

    private String nextPageToken(OutboundResponse response) {
        Optional<String> link = response.firstHeader("Link");
        if (link.isEmpty()) {
            return null;
        }
        Matcher matcher = NEXT_LINK.matcher(link.get());
        return matcher.find() ? matcher.group(1) : null;
    }

    private ExternalOrder mapOrder(JsonNode orderNode) {
        String externalOrderId = text(orderNode, "id");
        String rawName = text(orderNode, "name");
        String orderNumber = "ORD-SHP-" + (hasText(rawName) ? stripLeadingHash(rawName) : externalOrderId);

        JsonNode customer = orderNode.path("customer");
        String customerName = customerName(
                text(customer, "first_name"),
                text(customer, "last_name")
        );
        String customerEmail = text(customer, "email");

        JsonNode shippingAddress = orderNode.path("shipping_address");
        String address = formatAddress(shippingAddress);

        String shippingAmount = orderNode.path("total_shipping_price_set")
                .path("shop_money")
                .path("amount")
                .asString("0.00");

        String currency = text(orderNode, "currency");
        if (!hasText(currency)) {
            currency = text(orderNode, "presentment_currency");
        }

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
                decimal(shippingAmount),
                currency,
                items
        );
    }

    private String formatAddress(JsonNode address) {
        String line1 = text(address, "address1");
        String line2 = text(address, "address2");
        String zip = text(address, "zip");
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

    private String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private BigDecimal decimal(String value) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }
}
