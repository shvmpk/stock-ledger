package com.shvmpk.stock_ledger.unit.inventory;

import com.shvmpk.stock_ledger.inventory.dto.MovementSummaryResponse;
import com.shvmpk.stock_ledger.inventory.dto.ReservationHolderResponse;
import com.shvmpk.stock_ledger.inventory.dto.StockExplanationResponse;
import com.shvmpk.stock_ledger.inventory.entity.StockMovementReason;
import com.shvmpk.stock_ledger.order.entity.OrderStatus;
import com.shvmpk.stock_ledger.inventory.service.StockExplanationComposer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Stock Explanation Composer")
class StockExplanationComposerTest {

    private final StockExplanationComposer composer = new StockExplanationComposer();

    @Test
    @DisplayName("Explains the ledger equation, open reservations, movement reasons, and the reorder level")
    void shouldExplainEachNumberInPlainWords() {
        StockExplanationResponse facts = explanation(
                8,
                6,
                2,
                18,
                true,
                List.of(
                        new ReservationHolderResponse(
                                UUID.fromString("00000000-0000-4000-8000-000000001042"),
                                "ORD-2026-1042",
                                OrderStatus.PENDING,
                                4,
                                OffsetDateTime.parse("2026-10-29T10:00:00Z")
                        ),
                        new ReservationHolderResponse(
                                UUID.fromString("00000000-0000-4000-8000-000000001043"),
                                "ORD-2026-1043",
                                OrderStatus.READY_TO_SHIP,
                                2,
                                OffsetDateTime.parse("2026-10-30T11:30:00Z")
                        )
                ),
                List.of(
                        new MovementSummaryResponse(
                                StockMovementReason.ORDER_RESERVED,
                                "Reserved for orders",
                                3,
                                0,
                                6,
                                OffsetDateTime.parse("2026-10-30T09:15:00Z")
                        ),
                        new MovementSummaryResponse(
                                StockMovementReason.RECEIVE,
                                "Received",
                                2,
                                30,
                                0,
                                OffsetDateTime.parse("2026-10-28T08:00:00Z")
                        )
                )
        );

        assertThat(composer.compose(facts)).containsExactly(
                "There are 8 units on the shelf, 6 reserved for open orders, leaving 2 available to sell.",
                "The reserved units sit on 2 open orders: ORD-2026-1042 (4) and ORD-2026-1043 (2).",
                "In the last 90 days: Reserved for orders 3 times (+6 reserved); Received 2 times (+30 on hand). "
                        + "Latest movement 30 Oct 2026.",
                "The reorder level is 18, so 2 available is at or below it and the item appears on the reorder list."
        );
    }

    @Test
    @DisplayName("Honestly states an empty ledger, an unknown reorder level, and retirement")
    void shouldExplainAbsencesWithoutInventingNumbers() {
        StockExplanationResponse facts = explanation(
                0,
                0,
                0,
                null,
                false,
                List.of(),
                List.of()
        );

        assertThat(composer.compose(facts)).containsExactly(
                "There are 0 units on the shelf, 0 reserved for open orders, leaving 0 available to sell.",
                "No stock movements were recorded in the last 90 days.",
                "No reorder level is set, so this variant is never flagged as low stock.",
                "This variant is retired, so it cannot be ordered or received."
        );
    }

    private StockExplanationResponse explanation(
            int onHand,
            int reserved,
            int available,
            Integer reorderLevel,
            boolean sellable,
            List<ReservationHolderResponse> reservations,
            List<MovementSummaryResponse> movements
    ) {
        return new StockExplanationResponse(
                UUID.fromString("00000000-0000-4000-8000-000000001011"),
                "HOOD-NVY-XL",
                "Heavyweight Cotton Hoodie",
                "Size / Color",
                "XL / Navy",
                onHand,
                reserved,
                available,
                reorderLevel,
                sellable,
                90,
                OffsetDateTime.parse("2026-08-03T00:00:00Z"),
                reservations,
                movements,
                List.of(),
                OffsetDateTime.parse("2026-11-01T09:00:00Z")
        );
    }
}
