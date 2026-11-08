package com.shvmpk.stock_ledger.platform.controller;

import com.shvmpk.stock_ledger.common.api.ApiResponse;
import com.shvmpk.stock_ledger.common.api.PaginationMeta;
import com.shvmpk.stock_ledger.config.security.CurrentUserResolver;
import com.shvmpk.stock_ledger.platform.dto.CreatePlatformConnectionRequest;
import com.shvmpk.stock_ledger.platform.dto.PlatformCatalogEntryResponse;
import com.shvmpk.stock_ledger.platform.dto.PlatformConnectionEventResponse;
import com.shvmpk.stock_ledger.platform.dto.PlatformConnectionResponse;
import com.shvmpk.stock_ledger.platform.dto.PlatformConnectionTestResponse;
import com.shvmpk.stock_ledger.platform.dto.PlatformSyncResponse;
import com.shvmpk.stock_ledger.platform.dto.UpdatePlatformConnectionRequest;
import com.shvmpk.stock_ledger.platform.service.PlatformConnectionCreationResult;
import com.shvmpk.stock_ledger.platform.service.PlatformConnectionEventQueryService;
import com.shvmpk.stock_ledger.platform.service.PlatformConnectionService;
import com.shvmpk.stock_ledger.platform.service.PlatformConnectionTestService;
import com.shvmpk.stock_ledger.platform.service.PlatformSyncService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platforms")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('OWNER')")
public class PlatformConnectionController {

    private static final int MAX_PAGE_SIZE = 100;

    private final PlatformConnectionService platformConnectionService;
    private final PlatformConnectionEventQueryService platformConnectionEventQueryService;
    private final PlatformConnectionTestService platformConnectionTestService;
    private final PlatformSyncService platformSyncService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PlatformConnectionResponse>>> listPlatforms(
            @RequestParam(required = false) Boolean active
    ) {
        return ResponseEntity.ok(ApiResponse.ok(platformConnectionService.list(active)));
    }

    @GetMapping("/catalog")
    public ResponseEntity<ApiResponse<List<PlatformCatalogEntryResponse>>> listPlatformCatalogue() {
        return ResponseEntity.ok(ApiResponse.ok(PlatformCatalogEntryResponse.catalogue()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PlatformConnectionResponse>> getPlatform(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(platformConnectionService.get(id)));
    }

    @GetMapping("/{id}/events")
    public ResponseEntity<ApiResponse<List<PlatformConnectionEventResponse>>> listConnectionHistory(
            @PathVariable UUID id,

            @RequestParam(value = "page", defaultValue = "0")
            @Min(value = 0, message = "Page index must be zero or greater") int page,

            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(
                page,
                safeSize,
                Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"))
        );

        Page<PlatformConnectionEventResponse> events =
                platformConnectionEventQueryService.history(id, pageable);
        PaginationMeta pagination = PaginationMeta.of(
                events.getNumber(),
                events.getSize(),
                events.getTotalElements()
        );

        return ResponseEntity.ok(ApiResponse.paginated(events.getContent(), pagination));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PlatformConnectionResponse>> createPlatform(
            @Valid @RequestBody CreatePlatformConnectionRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        UUID actorId = currentUserResolver.currentUserIdOrNull();
        PlatformConnectionCreationResult result =
                platformConnectionService.create(request, actorId, idempotencyKey);
        if (result.replayed()) {
            return ResponseEntity.ok(ApiResponse.ok(result.connection()));
        }
        return ResponseEntity
                .created(URI.create("/api/v1/platforms/" + result.connection().id()))
                .body(ApiResponse.ok(result.connection()));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<PlatformConnectionResponse>> updatePlatform(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePlatformConnectionRequest request
    ) {
        UUID actorId = currentUserResolver.currentUserIdOrNull();
        return ResponseEntity.ok(ApiResponse.ok(platformConnectionService.update(id, request, actorId)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<PlatformConnectionResponse>> deactivatePlatform(
            @PathVariable UUID id,
            @RequestParam(value = "reason", required = false)
            @Size(max = 500, message = "Pause reason must be at most 500 characters.")
            String reason
    ) {
        UUID actorId = currentUserResolver.currentUserIdOrNull();
        return ResponseEntity.ok(ApiResponse.ok(
                platformConnectionService.deactivate(id, reason, actorId)
        ));
    }

    @PostMapping("/{id}/sync")
    public ResponseEntity<ApiResponse<PlatformSyncResponse>> triggerSync(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(platformSyncService.syncConnection(id)));
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<ApiResponse<PlatformConnectionTestResponse>> testConnection(
            @PathVariable UUID id
    ) {
        UUID actorId = currentUserResolver.currentUserIdOrNull();
        return ResponseEntity.ok(ApiResponse.ok(platformConnectionTestService.test(id, actorId)));
    }
}
