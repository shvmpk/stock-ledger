package com.shvmpk.stock_ledger.unit.platform;

import com.shvmpk.stock_ledger.platform.adapter.ExternalOrder;
import com.shvmpk.stock_ledger.platform.adapter.PlatformOrderPage;
import com.shvmpk.stock_ledger.platform.adapter.SyncWindow;
import com.shvmpk.stock_ledger.platform.adapter.WooCommerceOrderAdapter;
import com.shvmpk.stock_ledger.platform.entity.PlatformCredentials;
import com.shvmpk.stock_ledger.platform.entity.PlatformType;
import com.shvmpk.stock_ledger.platform.outbound.OutboundRequest;
import com.shvmpk.stock_ledger.platform.outbound.OutboundResponse;
import com.shvmpk.stock_ledger.platform.service.PlatformSyncException;
import com.shvmpk.stock_ledger.platform.service.PlatformSyncTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("WooCommerceOrderAdapter Unit Tests")
class WooCommerceOrderAdapterTest {

    private static final String ENDPOINT = "https://northwind-home.co.uk/wp-json/wc/v3";
    private static final String CONSUMER_KEY = "ck_8f2a6d1c4e9b7350a2f8d6c1";
    private static final String CONSUMER_SECRET = "dummy-woocommerce-secret-2";

    private final WooCommerceOrderAdapter adapter = new WooCommerceOrderAdapter(new ObjectMapper());

    @Test
    @DisplayName("Should build a WooCommerce orders request with page, window, and Basic auth")
    void shouldBuildWooCommerceOrdersRequest() {
        OffsetDateTime from = OffsetDateTime.parse("2026-10-01T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2026-10-23T00:00:00Z");
        PlatformSyncTarget target = target(ENDPOINT);

        OutboundRequest request = adapter.buildPageRequest(
                target,
                new PlatformCredentials(null, CONSUMER_KEY, CONSUMER_SECRET),
                new SyncWindow(from, to),
                null,
                Duration.ofSeconds(5)
        );

        String expectedAuthorization = "Basic " + Base64.getEncoder().encodeToString(
                (CONSUMER_KEY + ":" + CONSUMER_SECRET).getBytes(StandardCharsets.UTF_8)
        );

        assertThat(request.method()).isEqualTo("GET");
        assertThat(request.uri().getHost()).isEqualTo("northwind-home.co.uk");
        assertThat(request.uri().getPath()).isEqualTo("/wp-json/wc/v3/orders");
        assertThat(request.uri().getQuery())
                .contains("per_page=100")
                .contains("page=1")
                .contains("after=" + from)
                .contains("before=" + to);
        assertThat(request.firstHeader("Authorization")).contains(expectedAuthorization);
        assertThat(request.timeout()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("Should build a lightweight authenticated WooCommerce product request for a connection test")
    void shouldBuildWooCommerceTestRequest() {
        OutboundRequest request = adapter.buildTestRequest(
                target(ENDPOINT),
                new PlatformCredentials(null, CONSUMER_KEY, CONSUMER_SECRET),
                Duration.ofSeconds(5)
        );

        assertThat(request.uri().getHost()).isEqualTo("northwind-home.co.uk");
        assertThat(request.uri().getPath()).isEqualTo("/wp-json/wc/v3/products");
        assertThat(request.uri().getQuery()).isEqualTo("per_page=1");
        assertThat(request.firstHeader("Authorization")).isPresent();
    }

    @Test
    @DisplayName("Should advance WooCommerce pagination using X-WP-TotalPages")
    void shouldAdvanceWooCommercePagination() {
        OutboundResponse response = new OutboundResponse(
                200,
                Map.of("X-WP-TotalPages", List.of("3")),
                "[]"
        );

        PlatformOrderPage first = adapter.parsePage(response, null);
        PlatformOrderPage second = adapter.parsePage(response, "2");
        PlatformOrderPage last = adapter.parsePage(response, "3");
        PlatformOrderPage capped = adapter.parsePage(response, "20");

        assertThat(first.nextPageToken()).isEqualTo("2");
        assertThat(second.nextPageToken()).isEqualTo("3");
        assertThat(last.nextPageToken()).isNull();
        assertThat(capped.nextPageToken()).isNull();
    }

    @Test
    @DisplayName("Should parse WooCommerce orders, addresses, line items, and diagnostic currency")
    void shouldParseWooCommerceOrder() {
        String body = """
                [
                  {
                    "id": 2201,
                    "number": "1044",
                    "currency": "GBP",
                    "shipping_total": "5.25",
                    "billing": {
                      "first_name": "Freja",
                      "last_name": "Lindholm",
                      "email": "freja.lindholm@nordicliving.se"
                    },
                    "shipping": {
                      "address_1": "Drottninggatan 12",
                      "address_2": "",
                      "postcode": "411 14",
                      "city": "Gothenburg",
                      "country": "SE"
                    },
                    "line_items": [
                      {
                        "sku": "COF-ETH-250G",
                        "quantity": 1,
                        "price": "14.50"
                      }
                    ]
                  }
                ]
                """;

        PlatformOrderPage page = adapter.parsePage(
                new OutboundResponse(200, Map.of(), body),
                null
        );

        assertThat(page.orders()).hasSize(1);
        ExternalOrder order = page.orders().getFirst();
        assertThat(order.externalOrderId()).isEqualTo("2201");
        assertThat(order.orderNumber()).isEqualTo("ORD-WOO-1044");
        assertThat(order.customerName()).isEqualTo("Freja Lindholm");
        assertThat(order.customerEmail()).isEqualTo("freja.lindholm@nordicliving.se");
        assertThat(order.shippingAddress()).isEqualTo("Drottninggatan 12, 411 14 Gothenburg, SE");
        assertThat(order.shippingCharge()).isEqualByComparingTo("5.25");
        assertThat(order.currency()).isEqualTo("GBP");
        assertThat(order.items()).hasSize(1);
        assertThat(order.items().getFirst().sku()).isEqualTo("COF-ETH-250G");
        assertThat(order.items().getFirst().quantity()).isEqualTo(1);
        assertThat(order.items().getFirst().unitPrice()).isEqualByComparingTo("14.50");
    }

    @Test
    @DisplayName("Should reject a WooCommerce request without consumer credentials")
    void shouldRejectMissingConsumerCredentials() {
        assertThatThrownBy(() -> adapter.buildPageRequest(
                target(ENDPOINT),
                PlatformCredentials.empty(),
                new SyncWindow(OffsetDateTime.now().minusDays(1), OffsetDateTime.now()),
                null,
                Duration.ofSeconds(5)
        ))
                .isInstanceOf(PlatformSyncException.class)
                .hasMessageContaining("consumer key");
    }

    @Test
    @DisplayName("Should reject a WooCommerce test request without consumer credentials")
    void shouldRejectMissingConsumerCredentialsForTest() {
        assertThatThrownBy(() -> adapter.buildTestRequest(
                target(ENDPOINT),
                PlatformCredentials.empty(),
                Duration.ofSeconds(5)
        ))
                .isInstanceOf(PlatformSyncException.class)
                .hasMessageContaining("consumer key");
    }

    private PlatformSyncTarget target(String endpoint) {
        return new PlatformSyncTarget(
                UUID.randomUUID(),
                PlatformType.WOOCOMMERCE,
                endpoint,
                "v1.encrypted",
                null
        );
    }
}
