package com.shvmpk.stock_ledger.user.controller;

import com.shvmpk.stock_ledger.common.api.ApiResponse;
import com.shvmpk.stock_ledger.common.api.PaginationMeta;
import com.shvmpk.stock_ledger.common.error.InvalidDateRangeException;
import com.shvmpk.stock_ledger.user.dto.AuthEventResponse;
import com.shvmpk.stock_ledger.user.entity.AuthEventType;
import com.shvmpk.stock_ledger.user.service.AuthEventQueryService;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/auth-events")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class AuthEventController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AuthEventQueryService authEventQueryService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AuthEventResponse>>> listEvents(
            @RequestParam(value = "page", defaultValue = "0")
            @Min(value = 0, message = "Page index must be zero or greater") int page,

            @RequestParam(value = "size", defaultValue = "20") int size,

            @RequestParam(value = "eventType", required = false) List<AuthEventType> eventTypes,

            @RequestParam(value = "actorEmail", required = false)
            @Size(max = 255, message = "Actor email must be at most 255 characters") String actorEmail,

            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,

            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to
    ) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidDateRangeException();
        }

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(
                page,
                safeSize,
                Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"))
        );

        Page<AuthEventResponse> events = authEventQueryService.search(
                eventTypes,
                actorEmail,
                from,
                to,
                pageable
        );
        PaginationMeta pagination = PaginationMeta.of(
                events.getNumber(),
                events.getSize(),
                events.getTotalElements()
        );

        return ResponseEntity.ok(ApiResponse.paginated(events.getContent(), pagination));
    }
}
