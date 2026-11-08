package com.shvmpk.stock_ledger.analytics.controller;

import com.shvmpk.stock_ledger.analytics.dto.ReorderDetailResponse;
import com.shvmpk.stock_ledger.analytics.dto.ReorderListResponse;
import com.shvmpk.stock_ledger.analytics.dto.ShippingSummaryDto;
import com.shvmpk.stock_ledger.analytics.service.ReorderService;
import com.shvmpk.stock_ledger.analytics.service.ShippingAnalyticsService;
import com.shvmpk.stock_ledger.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final ShippingAnalyticsService shippingAnalyticsService;
    private final ReorderService reorderService;

    @GetMapping("/shipping-summary")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<List<ShippingSummaryDto>>> getShippingSummary(
            @RequestParam(required = false) String month
    ) {
        List<ShippingSummaryDto> summaries = shippingAnalyticsService.getMonthlyShippingSummaries(month);
        return ResponseEntity.ok(ApiResponse.ok(summaries));
    }

    @GetMapping("/reorder")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    public ResponseEntity<ApiResponse<ReorderListResponse>> getReorderList() {
        return ResponseEntity.ok(ApiResponse.ok(reorderService.getReorderList()));
    }

    @GetMapping("/reorder/{variantId}")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    public ResponseEntity<ApiResponse<ReorderDetailResponse>> getReorderDetail(
            @PathVariable UUID variantId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(reorderService.getDetail(variantId)));
    }
}
