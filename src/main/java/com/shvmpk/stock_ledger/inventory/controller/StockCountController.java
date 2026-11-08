package com.shvmpk.stock_ledger.inventory.controller;

import com.shvmpk.stock_ledger.common.api.ApiResponse;
import com.shvmpk.stock_ledger.common.api.PaginationMeta;
import com.shvmpk.stock_ledger.config.security.CurrentUserResolver;
import com.shvmpk.stock_ledger.inventory.dto.RecordStockCountItemRequest;
import com.shvmpk.stock_ledger.inventory.dto.StockCountResponse;
import com.shvmpk.stock_ledger.inventory.dto.StockCountSummaryResponse;
import com.shvmpk.stock_ledger.inventory.entity.StockCountStatus;
import com.shvmpk.stock_ledger.inventory.service.StockCountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stock/counts")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
public class StockCountController {

    private static final int MAX_PAGE_SIZE = 100;

    private final StockCountService stockCountService;
    private final CurrentUserResolver currentUserResolver;

    @PostMapping
    public ResponseEntity<ApiResponse<StockCountResponse>> startSession() {
        StockCountResponse response = stockCountService.start(
                currentUserResolver.currentUserIdOrNull()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @PostMapping("/{id}/items")
    public ResponseEntity<ApiResponse<StockCountResponse>> recordItem(
            @PathVariable UUID id,
            @Valid @RequestBody RecordStockCountItemRequest request
    ) {
        StockCountResponse response = stockCountService.recordItem(
                id,
                request,
                currentUserResolver.currentUserIdOrNull()
        );
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<StockCountResponse>> completeSession(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(stockCountService.complete(id)));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<StockCountResponse>> cancelSession(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(stockCountService.cancel(id)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StockCountResponse>> getSession(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(stockCountService.get(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<StockCountSummaryResponse>>> listSessions(
            @RequestParam(value = "status", required = false) StockCountStatus status,
            @RequestParam(value = "page", defaultValue = "0")
            @Min(value = 0, message = "Page index must be zero or greater") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(
                page,
                safeSize,
                Sort.by(Sort.Order.desc("startedAt"), Sort.Order.desc("id"))
        );
        Page<StockCountSummaryResponse> sessions = stockCountService.list(status, pageable);
        PaginationMeta pagination = PaginationMeta.of(
                sessions.getNumber(),
                sessions.getSize(),
                sessions.getTotalElements()
        );

        return ResponseEntity.ok(ApiResponse.paginated(sessions.getContent(), pagination));
    }
}
