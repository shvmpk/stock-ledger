package com.shvmpk.stock_ledger.integration.web;

import com.shvmpk.stock_ledger.common.error.GlobalExceptionHandler;
import com.shvmpk.stock_ledger.config.security.SecurityConfig;
import com.shvmpk.stock_ledger.config.security.WebhookRequestHasher;
import com.shvmpk.stock_ledger.config.security.WebhookSignatureFilter;
import com.shvmpk.stock_ledger.order.dto.OrderResponse;
import com.shvmpk.stock_ledger.order.entity.OrderStatus;
import com.shvmpk.stock_ledger.platform.controller.WebhookOrderController;
import com.shvmpk.stock_ledger.platform.dto.WebhookOrderPayload;
import com.shvmpk.stock_ledger.platform.service.WebhookOrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = WebhookOrderController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("WebhookOrderController Web Slice Tests (Channel 3 Inbound Webhooks)")
class WebhookOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WebhookOrderService webhookOrderService;

    @Test
    @DisplayName("Should delegate a verified webhook with its request hash and return 200 OK")
    void shouldDelegateVerifiedWebhook() throws Exception {
        String payload = """
                {
                  "externalOrderId": "shp_9876543210",
                  "orderNumber": "ORD-SHP-2026-0099",
                  "customerName": "Astrid Lindqvist",
                  "customerEmail": "astrid.lindqvist@scandiboutique.se",
                  "shippingAddress": "Kungsgatan 44, 111 35 Stockholm, Sweden",
                  "shippingCharge": 5.99,
                  "items": [
                    {
                      "sku": "HOOD-NVY-XL",
                      "quantity": 1,
                      "unitPrice": 68.00
                    }
                  ]
                }
                """;
        String requestHash = WebhookRequestHasher.sha256Hex(payload);

        UUID orderId = UUID.randomUUID();
        OrderResponse response = new OrderResponse(
                orderId,
                "ORD-SHP-2026-0099",
                "WEBHOOK",
                OrderStatus.PENDING,
                "Astrid Lindqvist",
                "astrid.lindqvist@scandiboutique.se",
                "Kungsgatan 44, 111 35 Stockholm, Sweden",
                new BigDecimal("5.99"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(new OrderResponse.OrderItemResponse(
                        UUID.randomUUID(),
                        "HOOD-NVY-XL",
                        1,
                        new BigDecimal("68.00")
                )),
                OffsetDateTime.now()
        );

        when(webhookOrderService.processWebhookOrder(
                eq("shopify"),
                any(WebhookOrderPayload.class),
                eq(requestHash),
                isNull()
        )).thenReturn(response);

        mockMvc.perform(post("/api/v1/webhooks/orders/shopify")
                        .requestAttr(WebhookSignatureFilter.REQUEST_HASH_ATTRIBUTE, requestHash)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderNumber").value("ORD-SHP-2026-0099"))
                .andExpect(jsonPath("$.data.source").value("WEBHOOK"));

        verify(webhookOrderService).processWebhookOrder(
                eq("shopify"),
                any(WebhookOrderPayload.class),
                eq(requestHash),
                isNull()
        );
    }

    @Test
    @DisplayName("Should map an invalid signature domain error to 401 Problem Details")
    void shouldMapInvalidSignatureError() throws Exception {
        String payload = """
                {
                  "externalOrderId": "shp_9876543210",
                  "orderNumber": "ORD-SHP-2026-0099",
                  "customerName": "Astrid Lindqvist",
                  "customerEmail": "astrid.lindqvist@scandiboutique.se",
                  "shippingAddress": "Kungsgatan 44, 111 35 Stockholm, Sweden",
                  "shippingCharge": 5.99,
                  "items": [
                    {
                      "sku": "HOOD-NVY-XL",
                      "quantity": 1,
                      "unitPrice": 68.00
                    }
                  ]
                }
                """;

        when(webhookOrderService.processWebhookOrder(eq("shopify"), any(), any(), isNull()))
                .thenThrow(new com.shvmpk.stock_ledger.common.error.InvalidWebhookSignatureException("shopify"));

        mockMvc.perform(post("/api/v1/webhooks/orders/shopify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_WEBHOOK_SIGNATURE"));
    }
}
