package com.shvmpk.stock_ledger.analytics.service;

import com.shvmpk.stock_ledger.analytics.dto.ShippingSummaryDto;
import com.shvmpk.stock_ledger.order.repository.OrderRepository;
import com.shvmpk.stock_ledger.order.repository.ShippingSummaryProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ShippingAnalyticsService {

    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public List<ShippingSummaryDto> getMonthlyShippingSummaries(String period) {
        List<ShippingSummaryProjection> projections = orderRepository.findMonthlyShippingSummaries(period);
        return projections.stream()
                .map(p -> new ShippingSummaryDto(
                        p.getPeriod(),
                        p.getTotalShipped(),
                        p.getTotalCharged(),
                        p.getTotalCost(),
                        p.getTotalCharged().subtract(p.getTotalCost())
                ))
                .toList();
    }
}
