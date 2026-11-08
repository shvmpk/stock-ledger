package com.shvmpk.stock_ledger.integration.service;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import com.shvmpk.stock_ledger.analytics.dto.ShippingSummaryDto;
import com.shvmpk.stock_ledger.analytics.service.ShippingAnalyticsService;
import com.shvmpk.stock_ledger.order.entity.Order;
import com.shvmpk.stock_ledger.order.entity.OrderSource;
import com.shvmpk.stock_ledger.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
@DisplayName("Shipping Analytics Service Integration Tests (Task 6.1)")
class ShippingAnalyticsIntegrationTest {

    @Autowired
    private ShippingAnalyticsService shippingAnalyticsService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private com.shvmpk.stock_ledger.order.repository.OrderReturnRepository orderReturnRepository;

    @BeforeEach
    void setUp() {
        orderReturnRepository.deleteAll();
        orderRepository.deleteAll();

        // Order 1: Shipped with charge 12.50, cost 9.80
        Order o1 = Order.create(
                "ORD-ANL-001",
                OrderSource.MANUAL,
                null,
                "Freja Lindqvist",
                "freja.lindqvist@example.se",
                "Kungsgatan 14, 111 43 Stockholm, Sweden",
                new BigDecimal("12.50")
        );
        o1.markShipped("DHL", "MEDIUM", "1234567891", "https://track.dhl.com/1234567891", new BigDecimal("9.80"));
        orderRepository.save(o1);

        // Order 2: Shipped with charge 8.00, cost 6.50
        Order o2 = Order.create(
                "ORD-ANL-002",
                OrderSource.MANUAL,
                null,
                "Lars Berg",
                "lars.berg@example.se",
                "Drottninggatan 22, 411 07 Gothenburg, Sweden",
                new BigDecimal("8.00")
        );
        o2.markShipped("FEDEX", "SMALL", "987654321012", "https://track.fedex.com/987654321012", new BigDecimal("6.50"));
        orderRepository.save(o2);

        // Order 3: Pending order (NOT shipped) -> should be excluded from shipping costs
        Order o3 = Order.create(
                "ORD-ANL-003",
                OrderSource.MANUAL,
                null,
                "Astrid Holm",
                "astrid.holm@example.se",
                "Storgatan 5, 211 41 Malmo, Sweden",
                new BigDecimal("15.00")
        );
        orderRepository.save(o3);
    }

    @Test
    @DisplayName("Should aggregate shipping charges, actual carrier costs, and calculate net margin")
    void shouldAggregateShippingSummaryCorrectly() {
        List<ShippingSummaryDto> summaries = shippingAnalyticsService.getMonthlyShippingSummaries(null);

        assertThat(summaries).isNotEmpty();
        ShippingSummaryDto currentMonth = summaries.get(0);

        assertThat(currentMonth.totalOrdersShipped()).isEqualTo(2);
        assertThat(currentMonth.totalShippingCharged()).isEqualByComparingTo(new BigDecimal("20.50"));
        assertThat(currentMonth.totalShippingCost()).isEqualByComparingTo(new BigDecimal("16.30"));
        assertThat(currentMonth.netMargin()).isEqualByComparingTo(new BigDecimal("4.20"));
    }
}
