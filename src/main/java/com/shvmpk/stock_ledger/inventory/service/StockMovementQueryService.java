package com.shvmpk.stock_ledger.inventory.service;

import com.shvmpk.stock_ledger.inventory.dto.StockMovementResponse;
import com.shvmpk.stock_ledger.inventory.entity.StockMovementReason;
import com.shvmpk.stock_ledger.inventory.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class StockMovementQueryService {

    private final StockMovementRepository stockMovementRepository;

    @Transactional(readOnly = true)
    public Page<StockMovementResponse> search(
            String sku,
            StockMovementReason reason,
            OffsetDateTime from,
            OffsetDateTime to,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );

        return stockMovementRepository.search(normalizeSku(sku), reason, from, to, pageable)
                .map(StockMovementResponse::from);
    }

    private String normalizeSku(String sku) {
        if (sku == null || sku.isBlank()) {
            return null;
        }
        return sku.trim();
    }
}
