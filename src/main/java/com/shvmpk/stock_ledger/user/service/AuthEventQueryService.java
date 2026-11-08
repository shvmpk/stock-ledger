package com.shvmpk.stock_ledger.user.service;

import com.shvmpk.stock_ledger.user.dto.AuthEventResponse;
import com.shvmpk.stock_ledger.user.entity.AuthEvent;
import com.shvmpk.stock_ledger.user.entity.AuthEventType;
import com.shvmpk.stock_ledger.user.repository.AuthEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Read side of the security audit trail. Filtering uses explicit
 * specifications so nullable parameters never become untyped JPQL parameters.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthEventQueryService {

    private final AuthEventRepository authEventRepository;

    public Page<AuthEventResponse> search(
            List<AuthEventType> eventTypes,
            String actorEmail,
            OffsetDateTime from,
            OffsetDateTime to,
            Pageable pageable
    ) {
        List<Specification<AuthEvent>> specifications = new ArrayList<>();

        if (eventTypes != null && !eventTypes.isEmpty()) {
            specifications.add((root, query, criteriaBuilder) ->
                    root.get("eventType").in(eventTypes));
        }
        if (actorEmail != null && !actorEmail.isBlank()) {
            String normalizedEmail = actorEmail.trim().toLowerCase(Locale.ROOT);
            specifications.add((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(criteriaBuilder.lower(root.get("actorEmail")), normalizedEmail));
        }
        if (from != null) {
            specifications.add((root, query, criteriaBuilder) ->
                    criteriaBuilder.greaterThanOrEqualTo(root.get("occurredAt"), from));
        }
        if (to != null) {
            specifications.add((root, query, criteriaBuilder) ->
                    criteriaBuilder.lessThanOrEqualTo(root.get("occurredAt"), to));
        }

        Specification<AuthEvent> specification = specifications.isEmpty()
                ? Specification.unrestricted()
                : Specification.allOf(specifications);

        return authEventRepository.findAll(specification, pageable).map(AuthEventResponse::from);
    }
}
