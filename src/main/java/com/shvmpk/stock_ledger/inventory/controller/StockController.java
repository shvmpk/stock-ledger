package com.shvmpk.stock_ledger.inventory.controller;

import com.shvmpk.stock_ledger.common.api.ApiResponse;
import com.shvmpk.stock_ledger.common.api.PaginationMeta;
import com.shvmpk.stock_ledger.config.security.CurrentUserResolver;
import com.shvmpk.stock_ledger.inventory.dto.ReceiveStockRequest;
import com.shvmpk.stock_ledger.inventory.dto.ReceiveStockResult;
import com.shvmpk.stock_ledger.inventory.dto.StockCountRequest;
import com.shvmpk.stock_ledger.inventory.dto.StockExplanationResponse;
import com.shvmpk.stock_ledger.inventory.dto.StockMovementResponse;
import com.shvmpk.stock_ledger.inventory.entity.StockMovementReason;
import com.shvmpk.stock_ledger.inventory.service.StockAuditResult;
import com.shvmpk.stock_ledger.inventory.service.StockAuditService;
import com.shvmpk.stock_ledger.inventory.service.StockExplanationService;
import com.shvmpk.stock_ledger.inventory.service.StockMovementQueryService;
import com.shvmpk.stock_ledger.inventory.service.StockReceivingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stock")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
public class StockController {

    private final StockAuditService stockAuditService;
    private final StockMovementQueryService stockMovementQueryService;
    private final StockReceivingService stockReceivingService;
    private final StockExplanationService stockExplanationService;
    private final CurrentUserResolver currentUserResolver;

    @PostMapping("/receive")
    public ResponseEntity<ApiResponse<ReceiveStockResult>> receiveStock(@Valid @RequestBody ReceiveStockRequest request) {
        ReceiveStockResult result = stockReceivingService.receive(
                request.productVariantId(),
                request.quantity(),
                currentUserResolver.currentUserIdOrNull(),
                request.notes() != null ? request.notes() : "Inbound stock receiving"
        );

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping("/count")
    public ResponseEntity<ApiResponse<StockAuditResult>> adjustCount(@Valid @RequestBody StockCountRequest request) {
        StockAuditResult result = stockAuditService.adjustPhysicalCount(
                request.productVariantId(),
                request.countedOnHand(),
                null,
                currentUserResolver.currentUserIdOrNull(),
                request.notes() != null ? request.notes() : "Physical count adjustment"
        );

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/variants/{variantId}/explanation")
    public ResponseEntity<ApiResponse<StockExplanationResponse>> explainStock(
            @PathVariable UUID variantId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(stockExplanationService.explain(variantId)));
    }

    @GetMapping("/movements")
    public ResponseEntity<ApiResponse<List<StockMovementResponse>>> listMovements(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "sku", required = false) String sku,
            @RequestParam(value = "reason", required = false) StockMovementReason reason,
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to
    ) {
        Page<StockMovementResponse> movements = stockMovementQueryService.search(
                sku,
                reason,
                from,
                to,
                page,
                size
        );
        PaginationMeta pagination = PaginationMeta.of(
                movements.getNumber(),
                movements.getSize(),
                movements.getTotalElements()
        );

        return ResponseEntity.ok(ApiResponse.paginated(movements.getContent(), pagination));
    }
}
