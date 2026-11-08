package com.shvmpk.stock_ledger.unit.platform;

import com.shvmpk.stock_ledger.platform.adapter.ExternalOrder;
import com.shvmpk.stock_ledger.platform.adapter.PlatformOrderPage;
import com.shvmpk.stock_ledger.platform.adapter.ShopifyOrderAdapter;
import com.shvmpk.stock_ledger.platform.adapter.SyncWindow;
import com.shvmpk.stock_ledger.platform.entity.PlatformCredentials;
import com.shvmpk.stock_ledger.platform.entity.PlatformType;
import com.shvmpk.stock_ledger.platform.outbound.OutboundRequest;
import com.shvmpk.stock_ledger.platform.outbound.OutboundResponse;
import com.shvmpk.stock_ledger.platform.service.PlatformSyncException;
import com.shvmpk.stock_ledger.platform.service.PlatformSyncTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ShopifyOrderAdapter Unit Tests")
class ShopifyOrderAdapterTest {

    private static final String ENDPOINT = "https://northwind-supply.myshopify.com/admin/api/2026-10";
    private static final String ACCESS_TOKEN = "dummy-shopify-access-token";

    private final ShopifyOrderAdapter adapter = new ShopifyOrderAdapter(new ObjectMapper());

    @Test
    @DisplayName("Should build a Shopify orders request with date window and access token")
    void shouldBuildShopifyOrdersRequest() {
        OffsetDateTime from = OffsetDateTime.parse("2026-10-01T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2026-10-23T00:00:00Z");
        PlatformSyncTarget target = target(ENDPOINT);

        OutboundRequest request = adapter.buildPageRequest(
                target,
                new PlatformCredentials(ACCESS_TOKEN, null, null),
                new SyncWindow(from, to),
                null,
                Duration.ofSeconds(5)
        );

        assertThat(request.method()).isEqualTo("GET");
        assertThat(request.uri().getHost()).isEqualTo("northwind-supply.myshopify.com");
        assertThat(request.uri().getPath()).isEqualTo("/admin/api/2026-10/orders.json");
        assertThat(request.uri().getQuery())
                .contains("limit=250")
                .contains("status=any")
                .contains("created_at_min=" + from)
                .contains("created_at_max=" + to);
        assertThat(request.firstHeader("X-Shopify-Access-Token")).contains(ACCESS_TOKEN);
        assertThat(request.timeout()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("Should build a lightweight authenticated Shopify shop request for a connection test")
    void shouldBuildShopifyTestRequest() {
        OutboundRequest request = adapter.buildTestRequest(
                target(ENDPOINT),
                new PlatformCredentials(ACCESS_TOKEN, null, null),
                Duration.ofSeconds(5)
        );

        assertThat(request.uri().toString()).isEqualTo(ENDPOINT + "/shop.json");
        assertThat(request.firstHeader("X-Shopify-Access-Token")).contains(ACCESS_TOKEN);
        assertThat(request.firstHeader("Accept")).contains("application/json");
    }

    @Test
    @DisplayName("Should parse Shopify orders, line items, currency, and the Link next page")
    void shouldParseShopifyOrdersAndNextLink() {
        String body = """
                {
                  "orders": [
                    {
                      "id": 1099238475,
                      "name": "#1042",
                      "currency": "SEK",
                      "customer": {
                        "first_name": "Astrid",
                        "last_name": "Lindqvist",
                        "email": "astrid@scandiboutique.se"
                      },
                      "shipping_address": {
                        "address1": "Kungsgatan 44",
                        "address2": "",
                        "city": "Stockholm",
                        "zip": "111 35",
                        "country": "Sweden"
                      },
                      "total_shipping_price_set": {
                        "shop_money": { "amount": "4.99" }
                      },
                      "line_items": [
                        {
                          "sku": "COF-ETH-250G",
                          "quantity": 2,
                          "price": "14.50"
                        }
                      ]
                    }
                  ]
                }
                """;
        String nextUrl = ENDPOINT + "/orders.json?page_info=next-token";

        PlatformOrderPage page = adapter.parsePage(
                new OutboundResponse(
                        200,
                        Map.of("Link", List.of("<" + nextUrl + ">; rel=\"next\"")),
                        body
                ),
                null
        );

        assertThat(page.nextPageToken()).isEqualTo(nextUrl);
        assertThat(page.orders()).hasSize(1);

        ExternalOrder order = page.orders().getFirst();
        assertThat(order.externalOrderId()).isEqualTo("1099238475");
        assertThat(order.orderNumber()).isEqualTo("ORD-SHP-1042");
        assertThat(order.customerName()).isEqualTo("Astrid Lindqvist");
        assertThat(order.customerEmail()).isEqualTo("astrid@scandiboutique.se");
        assertThat(order.shippingAddress()).isEqualTo("Kungsgatan 44, 111 35 Stockholm, Sweden");
        assertThat(order.shippingCharge()).isEqualByComparingTo("4.99");
        assertThat(order.currency()).isEqualTo("SEK");
        assertThat(order.items()).hasSize(1);
        assertThat(order.items().getFirst().sku()).isEqualTo("COF-ETH-250G");
        assertThat(order.items().getFirst().quantity()).isEqualTo(2);
        assertThat(order.items().getFirst().unitPrice()).isEqualByComparingTo("14.50");
    }

    @Test
    @DisplayName("Should reject a Shopify request without an access token")
    void shouldRejectMissingAccessToken() {
        assertThatThrownBy(() -> adapter.buildPageRequest(
                target(ENDPOINT),
                PlatformCredentials.empty(),
                new SyncWindow(OffsetDateTime.now().minusDays(1), OffsetDateTime.now()),
                null,
                Duration.ofSeconds(5)
        ))
                .isInstanceOf(PlatformSyncException.class)
                .hasMessageContaining("access token");
    }

    @Test
    @DisplayName("Should reject a Shopify test request without an access token")
    void shouldRejectMissingAccessTokenForTest() {
        assertThatThrownBy(() -> adapter.buildTestRequest(
                target(ENDPOINT),
                PlatformCredentials.empty(),
                Duration.ofSeconds(5)
        ))
                .isInstanceOf(PlatformSyncException.class)
                .hasMessageContaining("access token");
    }

    private PlatformSyncTarget target(String endpoint) {
        return new PlatformSyncTarget(
                UUID.randomUUID(),
                PlatformType.SHOPIFY,
                endpoint,
                "v1.encrypted",
                null
        );
    }
}
