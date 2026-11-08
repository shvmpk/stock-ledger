package com.shvmpk.stock_ledger.order.controller;

import com.shvmpk.stock_ledger.common.api.ApiResponse;
import com.shvmpk.stock_ledger.common.api.PaginationMeta;
import com.shvmpk.stock_ledger.config.security.CurrentUserResolver;
import com.shvmpk.stock_ledger.order.dto.CreateOrderRequest;
import com.shvmpk.stock_ledger.order.dto.CsvImportResult;
import com.shvmpk.stock_ledger.order.dto.OrderResponse;
import com.shvmpk.stock_ledger.order.dto.OrderStatusSummaryResponse;
import com.shvmpk.stock_ledger.order.dto.ProcessReturnRequest;
import com.shvmpk.stock_ledger.order.dto.ReturnResponse;
import com.shvmpk.stock_ledger.order.dto.ShipOrderRequest;
import com.shvmpk.stock_ledger.order.entity.OrderStatus;
import com.shvmpk.stock_ledger.order.service.OrderCsvImportService;
import com.shvmpk.stock_ledger.order.service.OrderReturnService;
import com.shvmpk.stock_ledger.order.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private static final int MAX_PAGE_SIZE = 100;

    private final OrderService orderService;
    private final OrderCsvImportService csvImportService;
    private final OrderReturnService returnService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(@PathVariable UUID id) {
        OrderResponse response = orderService.getOrderById(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> listOrders(
            @RequestParam(value = "status", required = false) OrderStatus status,
            @RequestParam(value = "page", defaultValue = "0")
            @Min(value = 0, message = "Page index must be zero or greater") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(
                page,
                safeSize,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("orderNumber"))
        );
        Page<OrderResponse> orderPage = orderService.listOrders(status, pageable);
        PaginationMeta pagination = PaginationMeta.of(orderPage.getNumber(), orderPage.getSize(), orderPage.getTotalElements());
        return ResponseEntity.ok(ApiResponse.paginated(orderPage.getContent(), pagination));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<OrderStatusSummaryResponse>>> getStatusSummary() {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getStatusSummary()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    public ResponseEntity<ApiResponse<OrderResponse>> createManualOrder(@Valid @RequestBody CreateOrderRequest request) {
        OrderResponse response = orderService.createManualOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @PostMapping(value = "/import-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    public ResponseEntity<ApiResponse<CsvImportResult>> importCsv(@RequestParam("file") MultipartFile file) {
        try {
            CsvImportResult result = csvImportService.importOrdersFromCsv(file.getInputStream());
            return ResponseEntity.ok(ApiResponse.ok(result));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read uploaded CSV file", e);
        }
    }

    @PostMapping("/{id}/pack")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    public ResponseEntity<ApiResponse<OrderResponse>> packOrder(@PathVariable UUID id) {
        OrderResponse response = orderService.packOrder(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/{id}/ship")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    public ResponseEntity<ApiResponse<OrderResponse>> shipOrder(
            @PathVariable UUID id,
            @Valid @RequestBody ShipOrderRequest request
    ) {
        OrderResponse response = orderService.shipOrder(id, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
            @PathVariable UUID id,
            @RequestParam(value = "reason", defaultValue = "Cancelled by merchant") String reason
    ) {
        OrderResponse response = orderService.cancelOrder(id, reason);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/{id}/returns")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<ReturnResponse>>> getReturnHistory(@PathVariable UUID id) {

        return ResponseEntity.ok(ApiResponse.ok(returnService.getReturnsForOrder(id)));
    }

    @PostMapping("/{id}/returns")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    public ResponseEntity<ApiResponse<ReturnResponse>> processReturn(
            @PathVariable UUID id,
            @Valid @RequestBody ProcessReturnRequest request
    ) {
        ReturnResponse response = returnService.processReturn(
                id,
                request,
                currentUserResolver.currentUserIdOrNull()
        );
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
