package com.shvmpk.stock_ledger.inventory.service;

import com.shvmpk.stock_ledger.common.error.DuplicateStockCountItemException;
import com.shvmpk.stock_ledger.common.error.InvalidStockCountQuantityException;
import com.shvmpk.stock_ledger.common.error.InvalidStockCountStateException;
import com.shvmpk.stock_ledger.common.error.ResourceNotFoundException;
import com.shvmpk.stock_ledger.inventory.dto.RecordStockCountItemRequest;
import com.shvmpk.stock_ledger.inventory.dto.StockCountResponse;
import com.shvmpk.stock_ledger.inventory.dto.StockCountSummaryResponse;
import com.shvmpk.stock_ledger.inventory.entity.StockCount;
import com.shvmpk.stock_ledger.inventory.entity.StockCountItem;
import com.shvmpk.stock_ledger.inventory.entity.StockCountStatus;
import com.shvmpk.stock_ledger.inventory.repository.StockCountItemRepository;
import com.shvmpk.stock_ledger.inventory.repository.StockCountRepository;
import com.shvmpk.stock_ledger.product.entity.ProductVariant;
import com.shvmpk.stock_ledger.product.repository.ProductVariantRepository;
import com.shvmpk.stock_ledger.user.entity.User;
import com.shvmpk.stock_ledger.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockCountService {

    private final StockCountRepository stockCountRepository;
    private final StockCountItemRepository stockCountItemRepository;
    private final ProductVariantRepository productVariantRepository;
    private final UserRepository userRepository;
    private final StockAuditService stockAuditService;

    @Transactional
    public StockCountResponse start(UUID actorId) {
        User actor = resolveActor(actorId);
        StockCount session = StockCount.start(actor);
        return StockCountResponse.from(stockCountRepository.save(session));
    }

    @Transactional
    public StockCountResponse recordItem(
            UUID sessionId,
            RecordStockCountItemRequest request,
            UUID actorId
    ) {
        StockCount session = findSession(sessionId);
        if (!session.isInProgress()) {
            throw new InvalidStockCountStateException(session.getStatus());
        }
        if (request.countedOnHand() == null || request.countedOnHand() < 0) {
            throw new InvalidStockCountQuantityException();
        }

        ProductVariant variant = productVariantRepository.findById(request.productVariantId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ProductVariant",
                        request.productVariantId()
                ));

        if (stockCountItemRepository.existsByStockCountIdAndProductVariantId(
                sessionId,
                variant.getId()
        )) {
            throw new DuplicateStockCountItemException(variant.getSku());
        }

        resolveActor(actorId);

        String auditNotes = request.notes() != null && !request.notes().isBlank()
                ? request.notes()
                : "Stock count session item";

        StockAuditResult auditResult = stockAuditService.adjustPhysicalCount(
                variant.getId(),
                request.countedOnHand(),
                sessionId,
                actorId,
                auditNotes
        );

        StockCount refreshedSession = findSession(sessionId);
        ProductVariant refreshedVariant = productVariantRepository.findById(variant.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ProductVariant",
                        variant.getId()
                ));

        StockCountItem item = StockCountItem.create(
                refreshedSession,
                refreshedVariant,
                auditResult.previousOnHand(),
                auditResult.countedOnHand(),
                auditResult.variance(),
                request.notes(),
                auditResult.hasReservationDeficit(),
                auditResult.reservationDeficit()
        );
        refreshedSession.addItem(item);
        stockCountRepository.save(refreshedSession);

        return StockCountResponse.from(refreshedSession);
    }

    @Transactional
    public StockCountResponse complete(UUID sessionId) {
        StockCount session = findSession(sessionId);
        session.complete();
        stockCountRepository.save(session);
        return StockCountResponse.from(session);
    }

    @Transactional
    public StockCountResponse cancel(UUID sessionId) {
        StockCount session = findSession(sessionId);
        session.cancel();
        stockCountRepository.save(session);
        return StockCountResponse.from(session);
    }

    @Transactional(readOnly = true)
    public StockCountResponse get(UUID sessionId) {
        return StockCountResponse.from(findSession(sessionId));
    }

    @Transactional(readOnly = true)
    public Page<StockCountSummaryResponse> list(StockCountStatus status, Pageable pageable) {
        Page<StockCount> sessions = status == null
                ? stockCountRepository.findAllSessions(pageable)
                : stockCountRepository.findAllByStatus(status, pageable);

        return sessions.map(StockCountSummaryResponse::from);
    }

    private StockCount findSession(UUID sessionId) {
        return stockCountRepository.findWithItemsById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("StockCount", sessionId));
    }

    private User resolveActor(UUID actorId) {
        if (actorId == null) {
            return null;
        }
        return userRepository.findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException("User", actorId));
    }
}
