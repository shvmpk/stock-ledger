package com.shvmpk.stock_ledger.settings.controller;

import com.shvmpk.stock_ledger.common.api.ApiResponse;
import com.shvmpk.stock_ledger.config.security.CurrentUserResolver;
import com.shvmpk.stock_ledger.settings.dto.ReorderSettingsResponse;
import com.shvmpk.stock_ledger.settings.dto.UpdateReorderSettingsRequest;
import com.shvmpk.stock_ledger.settings.service.ReorderSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings/reorder")
@RequiredArgsConstructor
public class ReorderSettingsController {

    private final ReorderSettingsService reorderSettingsService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    public ResponseEntity<ApiResponse<ReorderSettingsResponse>> getSettings() {
        return ResponseEntity.ok(ApiResponse.ok(reorderSettingsService.getSettings()));
    }

    @PutMapping
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<ReorderSettingsResponse>> updateSettings(
            @Valid @RequestBody UpdateReorderSettingsRequest request
    ) {
        ReorderSettingsResponse response = reorderSettingsService.updateLeadTimeDays(
                request.leadTimeDays(),
                currentUserResolver.currentUserIdOrNull()
        );
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
