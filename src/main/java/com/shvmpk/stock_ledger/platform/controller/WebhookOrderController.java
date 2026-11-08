package com.shvmpk.stock_ledger.platform.controller;

import com.shvmpk.stock_ledger.common.api.ApiResponse;
import com.shvmpk.stock_ledger.config.security.WebhookSignatureFilter;
import com.shvmpk.stock_ledger.order.dto.OrderResponse;
import com.shvmpk.stock_ledger.platform.dto.WebhookOrderPayload;
import com.shvmpk.stock_ledger.platform.service.WebhookOrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/webhooks/orders")
@RequiredArgsConstructor
public class WebhookOrderController {

    private final WebhookOrderService webhookOrderService;

    @PostMapping("/{platform}")
    public ResponseEntity<ApiResponse<OrderResponse>> handleWebhookOrder(
            @PathVariable String platform,
            @Valid @RequestBody WebhookOrderPayload payload,
            HttpServletRequest request
    ) {
        String requestHash = (String) request.getAttribute(WebhookSignatureFilter.REQUEST_HASH_ATTRIBUTE);
        UUID connectionId = (UUID) request.getAttribute(WebhookSignatureFilter.CONNECTION_ID_ATTRIBUTE);
        OrderResponse response = webhookOrderService.processWebhookOrder(
                platform,
                payload,
                requestHash,
                connectionId
        );
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
