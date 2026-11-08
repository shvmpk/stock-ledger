package com.shvmpk.stock_ledger.platform.service;

import com.shvmpk.stock_ledger.common.error.IdempotencyKeyReusedException;
import com.shvmpk.stock_ledger.common.error.OrderNumberConflictException;
import com.shvmpk.stock_ledger.common.error.ResourceNotFoundException;
import com.shvmpk.stock_ledger.common.error.RetiredProductException;
import com.shvmpk.stock_ledger.common.error.UnsupportedPlatformException;
import com.shvmpk.stock_ledger.order.entity.Order;
import com.shvmpk.stock_ledger.order.entity.OrderItem;
import com.shvmpk.stock_ledger.order.entity.OrderSource;
import com.shvmpk.stock_ledger.order.repository.OrderRepository;
import com.shvmpk.stock_ledger.platform.dto.WebhookOrderPayload;
import com.shvmpk.stock_ledger.platform.entity.IdempotencyRecord;
import com.shvmpk.stock_ledger.platform.entity.PlatformType;
import com.shvmpk.stock_ledger.platform.repository.IdempotencyRecordRepository;
import com.shvmpk.stock_ledger.product.entity.ProductVariant;
import com.shvmpk.stock_ledger.product.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookOrderIntakeService {

    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final OrderRepository orderRepository;
    private final ProductVariantRepository productVariantRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WebhookIntakeResult createWaitingOrder(
            String platformName,
            WebhookOrderPayload payload,
            String requestHash
    ) {
        PlatformType platformType = resolvePlatformType(platformName);
        String idempotencyKey = platformType.name().toLowerCase(Locale.ROOT)
                + ":"
                + payload.externalOrderId();

        Optional<IdempotencyRecord> existingRecord =
                idempotencyRecordRepository.findByIdempotencyKey(idempotencyKey);
        if (existingRecord.isPresent()) {
            IdempotencyRecord record = existingRecord.get();
            if (!Objects.equals(record.getRequestHash(), requestHash)) {
                log.warn(
                        "Idempotency key {} was reused with a different request hash for platform {}.",
                        idempotencyKey,
                        platformName
                );
                throw new IdempotencyKeyReusedException();
            }
            log.info("Duplicate webhook received with idempotency key {}. Returning cached order.", idempotencyKey);
            return new WebhookIntakeResult(
                    resolveCachedOrderId(record, payload.orderNumber()),
                    true,
                    List.of()
            );
        }

        Optional<Order> existingOrder = orderRepository.findByOrderNumber(payload.orderNumber());
        if (existingOrder.isPresent()) {
            Order order = existingOrder.get();
            if (order.getSource() == OrderSource.WEBHOOK
                    && Objects.equals(order.getExternalOrderId(), payload.externalOrderId())) {
                log.info(
                        "Webhook order number {} already belongs to the same external order. Returning existing order.",
                        payload.orderNumber()
                );
                idempotencyRecordRepository.saveAndFlush(IdempotencyRecord.create(
                        idempotencyKey,
                        requestHash,
                        HttpStatus.OK.value(),
                        order.getId().toString(),
                        7
                ));
                return new WebhookIntakeResult(order.getId(), true, List.of());
            }
            throw new OrderNumberConflictException(payload.orderNumber());
        }

        Order order = Order.createWaiting(
                payload.orderNumber(),
                OrderSource.WEBHOOK,
                payload.externalOrderId(),
                payload.customerName(),
                payload.customerEmail(),
                payload.shippingAddress(),
                payload.shippingCharge()
        );

        List<UUID> variantIds = new ArrayList<>();
        for (WebhookOrderPayload.WebhookOrderItem itemPayload : payload.items()) {
            ProductVariant variant = productVariantRepository.findBySkuIgnoreCase(itemPayload.sku())
                    .orElseThrow(() -> new ResourceNotFoundException("ProductVariant", itemPayload.sku()));
            if (!variant.isSellable()) {
                throw new RetiredProductException(variant.getSku(), "ordered");
            }
            variantIds.add(variant.getId());
            order.addItem(OrderItem.createUnreserved(
                    order,
                    variant,
                    itemPayload.quantity(),
                    itemPayload.unitPrice()
            ));
        }

        Order savedOrder = orderRepository.saveAndFlush(order);
        idempotencyRecordRepository.saveAndFlush(IdempotencyRecord.create(
                idempotencyKey,
                requestHash,
                HttpStatus.OK.value(),
                savedOrder.getId().toString(),
                7
        ));

        return new WebhookIntakeResult(savedOrder.getId(), false, variantIds);
    }

    private UUID resolveCachedOrderId(IdempotencyRecord record, String orderNumber) {
        if (record.getResponseBody() != null && !record.getResponseBody().isBlank()) {
            try {
                UUID orderId = UUID.fromString(record.getResponseBody());
                if (orderRepository.existsById(orderId)) {
                    return orderId;
                }
            } catch (IllegalArgumentException ex) {
                log.warn(
                        "Idempotency record {} contains an invalid order identifier; falling back to order number.",
                        record.getId()
                );
            }
        }

        return orderRepository.findByOrderNumber(orderNumber)
                .map(Order::getId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderNumber));
    }

    private PlatformType resolvePlatformType(String platform) {
        try {
            return PlatformType.valueOf(platform.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new UnsupportedPlatformException(platform);
        }
    }

    public record WebhookIntakeResult(
            UUID orderId,
            boolean replayed,
            List<UUID> variantIds
    ) {
    }
}
