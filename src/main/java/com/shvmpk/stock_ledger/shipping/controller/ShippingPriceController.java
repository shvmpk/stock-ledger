package com.shvmpk.stock_ledger.shipping.controller;

import com.shvmpk.stock_ledger.common.api.ApiResponse;
import com.shvmpk.stock_ledger.shipping.dto.ShippingRateDto;
import com.shvmpk.stock_ledger.shipping.dto.UpdateShippingRateRequest;
import com.shvmpk.stock_ledger.shipping.service.ShippingPricingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/shipping/rates")
@RequiredArgsConstructor
public class ShippingPriceController {

    private final ShippingPricingService pricingService;

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<ShippingRateDto>>> getAllRates() {
        List<ShippingRateDto> rates = pricingService.getAllRates();
        return ResponseEntity.ok(ApiResponse.ok(rates));
    }

    @PutMapping
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<ShippingRateDto>> configureRate(@Valid @RequestBody UpdateShippingRateRequest request) {
        ShippingRateDto rate = pricingService.configureRate(request);
        return ResponseEntity.ok(ApiResponse.ok(rate));
    }
}
