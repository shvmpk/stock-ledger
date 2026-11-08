package com.shvmpk.stock_ledger.platform.service;

import com.shvmpk.stock_ledger.order.dto.OrderResponse;
import com.shvmpk.stock_ledger.order.service.BacklogAllocationService;
import com.shvmpk.stock_ledger.order.service.OrderService;
import com.shvmpk.stock_ledger.platform.dto.WebhookOrderPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookOrderService {

    private final WebhookOrderIntakeService webhookOrderIntakeService;
    private final BacklogAllocationService backlogAllocationService;
    private final OrderService orderService;
    private final PlatformInboundOrderTracker inboundOrderTracker;

    public OrderResponse processWebhookOrder(
            String platformName,
            WebhookOrderPayload payload,
            String requestHash
    ) {
        return processWebhookOrder(platformName, payload, requestHash, null);
    }

    public OrderResponse processWebhookOrder(
            String platformName,
            WebhookOrderPayload payload,
            String requestHash,
            UUID connectionId
    ) {
        WebhookOrderIntakeService.WebhookIntakeResult intake =
                webhookOrderIntakeService.createWaitingOrder(platformName, payload, requestHash);

        if (!intake.replayed()) {
            inboundOrderTracker.recordInboundOrder(connectionId, OffsetDateTime.now(ZoneOffset.UTC));
            intake.variantIds().stream()
                    .distinct()
                    .forEach(variantId -> allocate(variantId, intake.orderId()));
        }

        return orderService.getOrderById(intake.orderId());
    }

    private void allocate(UUID variantId, UUID orderId) {
        try {
            backlogAllocationService.allocateWaitingForVariant(variantId, null);
        } catch (RuntimeException ex) {
            log.error(
                    "Webhook order {} was accepted but backlog allocation failed for variant {}. "
                            + "It will remain waiting for stock.",
                    orderId,
                    variantId,
                    ex
            );
        }
    }
}
