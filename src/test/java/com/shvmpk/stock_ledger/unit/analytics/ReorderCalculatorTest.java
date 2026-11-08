package com.shvmpk.stock_ledger.unit.analytics;

import com.shvmpk.stock_ledger.analytics.dto.ReorderHistoryStatus;
import com.shvmpk.stock_ledger.analytics.service.ReorderCalculator;
import com.shvmpk.stock_ledger.analytics.service.ReorderEstimate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Reorder Calculator Unit Tests (Task 3.1)")
class ReorderCalculatorTest {

    private final ReorderCalculator calculator = new ReorderCalculator();
    private final OffsetDateTime now = OffsetDateTime.parse("2026-11-01T09:00:00Z");

    @Test
    @DisplayName("Estimates cover and suggested quantity from ninety-day sales velocity")
    void shouldEstimateCoverAndSuggestedQuantity() {
        ReorderEstimate estimate = calculator.estimate(
                3,
                54,
                now.minusDays(60),
                14,
                now
        );

        assertThat(estimate.historyStatus()).isEqualTo(ReorderHistoryStatus.SUFFICIENT);
        assertThat(estimate.daysOfCover())
                .as("3 sellable units at 0.6 units/day leaves five full days of cover")
                .isEqualTo(5);
        assertThat(estimate.suggestedQuantity())
                .as("Lead time plus a fortnight of cover, less what is already sellable")
                .isEqualTo(14);
    }

    @Test
    @DisplayName("Never suggests a negative order when stock already covers the target")
    void shouldNotSuggestNegativeQuantity() {
        ReorderEstimate estimate = calculator.estimate(
                100,
                54,
                now.minusDays(60),
                14,
                now
        );

        assertThat(estimate.suggestedQuantity()).isZero();
        assertThat(estimate.daysOfCover()).isEqualTo(166);
    }

    @Test
    @DisplayName("An out-of-stock item has zero days of cover and still shows what to order")
    void shouldReportOutOfStockAsZeroCover() {
        ReorderEstimate estimate = calculator.estimate(
                0,
                54,
                now.minusDays(60),
                14,
                now
        );

        assertThat(estimate.daysOfCover()).isZero();
        assertThat(estimate.suggestedQuantity()).isEqualTo(17);
    }

    @Test
    @DisplayName("Withholds an estimate until at least twenty-one days of sales history exist")
    void shouldWithholdEstimateWithoutThreeWeeksOfHistory() {
        ReorderEstimate estimate = calculator.estimate(
                2,
                50,
                now.minusDays(10),
                14,
                now
        );

        assertThat(estimate.historyStatus()).isEqualTo(ReorderHistoryStatus.INSUFFICIENT);
        assertThat(estimate.daysOfCover()).isNull();
        assertThat(estimate.suggestedQuantity()).isNull();
    }

    @Test
    @DisplayName("Withholds an estimate below five net units sold")
    void shouldWithholdEstimateBelowMinimumUnits() {
        ReorderEstimate estimate = calculator.estimate(
                2,
                4,
                now.minusDays(60),
                14,
                now
        );

        assertThat(estimate.historyStatus()).isEqualTo(ReorderHistoryStatus.INSUFFICIENT);
        assertThat(estimate.suggestedQuantity()).isNull();
    }

    @Test
    @DisplayName("Withholds an estimate for a variant with no shipped sales at all")
    void shouldWithholdEstimateWithoutAnySales() {
        ReorderEstimate estimate = calculator.estimate(2, 0, null, 14, now);

        assertThat(estimate.historyStatus()).isEqualTo(ReorderHistoryStatus.INSUFFICIENT);
        assertThat(estimate.daysOfCover()).isNull();
        assertThat(estimate.suggestedQuantity()).isNull();
    }

    @Test
    @DisplayName("A longer supplier lead time raises the suggested quantity")
    void shouldIncreaseSuggestionForLongerLeadTime() {
        ReorderEstimate shortLead = calculator.estimate(3, 54, now.minusDays(60), 14, now);
        ReorderEstimate longLead = calculator.estimate(3, 54, now.minusDays(60), 21, now);

        assertThat(longLead.suggestedQuantity()).isGreaterThan(shortLead.suggestedQuantity());
    }

    @Test
    @DisplayName("Keeps items that are out of stock, below their level, or short of target cover")
    void shouldDecideWhichItemsNeedAction() {
        ReorderEstimate outOfStock = calculator.estimate(0, 54, now.minusDays(60), 14, now);
        ReorderEstimate belowLevel = calculator.estimate(4, 0, null, 14, now);
        ReorderEstimate covered = calculator.estimate(100, 54, now.minusDays(60), 14, now);

        assertThat(calculator.needsAction(outOfStock, 0, 5)).isTrue();
        assertThat(calculator.needsAction(belowLevel, 4, 5)).isTrue();
        assertThat(calculator.needsAction(covered, 100, 5)).isFalse();
    }
}
