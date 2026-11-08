package com.shvmpk.stock_ledger.product.service;

import com.shvmpk.stock_ledger.product.entity.Product;
import com.shvmpk.stock_ledger.product.entity.ProductCatalogChangeType;
import com.shvmpk.stock_ledger.product.entity.ProductCatalogEvent;
import com.shvmpk.stock_ledger.product.entity.ProductVariant;
import com.shvmpk.stock_ledger.product.repository.ProductCatalogEventRepository;
import com.shvmpk.stock_ledger.user.entity.User;
import com.shvmpk.stock_ledger.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Writes the append-only catalogue history. The database trigger is the
 * immutable guarantee; this class is the only supported write path.
 *
 * <p>Writes are part of the caller's transaction, so a failed audit write
 * rolls the retirement or restoration back.</p>
 */
@Component
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ProductCatalogEventRecorder {

    private final ProductCatalogEventRepository eventRepository;
    private final UserRepository userRepository;

    public void recordRetired(Product product, ProductVariant variant, UUID actorId) {
        record(product, variant, ProductCatalogChangeType.RETIRED, actorId);
    }

    public void recordRestored(Product product, ProductVariant variant, UUID actorId) {
        record(product, variant, ProductCatalogChangeType.RESTORED, actorId);
    }

    private void record(
            Product product,
            ProductVariant variant,
            ProductCatalogChangeType changeType,
            UUID actorId
    ) {
        User actor = actorId == null ? null : userRepository.findById(actorId).orElse(null);
        ProductCatalogEvent event = ProductCatalogEvent.record(
                product,
                variant,
                changeType,
                actorId,
                actor == null ? null : actor.getEmail()
        );

        eventRepository.saveAndFlush(event);

        log.info(
                "Recorded product catalogue change {} for product {} variant {} by actor {}",
                changeType,
                product.getId(),
                variant == null ? null : variant.getId(),
                actorId
        );
    }
}
