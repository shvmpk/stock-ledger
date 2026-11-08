package com.shvmpk.stock_ledger.inventory.service;

import com.shvmpk.stock_ledger.inventory.dto.MovementSummaryResponse;
import com.shvmpk.stock_ledger.inventory.dto.ReservationHolderResponse;
import com.shvmpk.stock_ledger.inventory.dto.StockExplanationResponse;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Turns the explanation figures into plain-language sentences. The wording is
 * deliberately factual: it states the ledger equation, who holds reservations,
 * what moved recently, and what the reorder level means.
 */
@Component
public class StockExplanationComposer {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK);

    public List<String> compose(StockExplanationResponse facts) {
        List<String> sentences = new ArrayList<>();
        sentences.add(String.format(
                "There are %d units on the shelf, %d reserved for open orders, leaving %d available to sell.",
                facts.onHandQuantity(),
                facts.reservedQuantity(),
                facts.availableQuantity()
        ));

        if (!facts.reservations().isEmpty()) {
            sentences.add(describeReservations(facts.reservations()));
        }

        sentences.add(describeMovements(facts));
        sentences.add(describeReorderLevel(facts));

        if (!facts.sellable()) {
            sentences.add("This variant is retired, so it cannot be ordered or received.");
        }

        return List.copyOf(sentences);
    }

    private String describeReservations(List<ReservationHolderResponse> reservations) {
        List<String> parts = reservations.stream()
                .limit(3)
                .map(reservation -> String.format(
                        "%s (%d)",
                        reservation.orderNumber(),
                        reservation.reservedQuantity()
                ))
                .toList();
        String joined = joinWithAnd(parts);
        if (reservations.size() > 3) {
            joined = joined + String.format(" plus %d more", reservations.size() - 3);
        }
        String orderWord = reservations.size() == 1 ? "order" : "orders";
        return String.format(
                "The reserved units sit on %d open %s: %s.",
                reservations.size(),
                orderWord,
                joined
        );
    }

    private String describeMovements(StockExplanationResponse facts) {
        if (facts.recentMovements().isEmpty()) {
            return String.format(
                    "No stock movements were recorded in the last %d days.",
                    facts.movementWindowDays()
            );
        }

        String breakdown = facts.recentMovements().stream()
                .map(this::describeMovement)
                .collect(Collectors.joining("; "));
        OffsetDateTime latest = facts.recentMovements().stream()
                .map(MovementSummaryResponse::latestAt)
                .max(OffsetDateTime::compareTo)
                .orElse(facts.generatedAt());

        return String.format(
                "In the last %d days: %s. Latest movement %s.",
                facts.movementWindowDays(),
                breakdown,
                DATE.format(latest)
        );
    }

    private String describeMovement(MovementSummaryResponse movement) {
        String times = movement.movementCount() == 1 ? "time" : "times";
        return String.format(
                "%s %d %s (%s)",
                movement.label(),
                movement.movementCount(),
                times,
                describeNetChange(movement.netOnHand(), movement.netReserved())
        );
    }

    private String describeNetChange(int netOnHand, int netReserved) {
        List<String> changes = new ArrayList<>();
        if (netOnHand != 0) {
            changes.add(signed(netOnHand) + " on hand");
        }
        if (netReserved != 0) {
            changes.add(signed(netReserved) + " reserved");
        }
        return changes.isEmpty() ? "no net change" : String.join(", ", changes);
    }

    private String signed(int value) {
        return value > 0 ? "+" + value : String.valueOf(value);
    }

    private String describeReorderLevel(StockExplanationResponse facts) {
        if (facts.reorderLevel() == null) {
            return "No reorder level is set, so this variant is never flagged as low stock.";
        }
        if (facts.availableQuantity() <= facts.reorderLevel()) {
            return String.format(
                    "The reorder level is %d, so %d available is at or below it and the item appears on the reorder list.",
                    facts.reorderLevel(),
                    facts.availableQuantity()
            );
        }
        return String.format(
                "The reorder level is %d, so %d available is above it and the item is not on the reorder list.",
                facts.reorderLevel(),
                facts.availableQuantity()
        );
    }

    private String joinWithAnd(List<String> parts) {
        if (parts.size() == 1) {
            return parts.getFirst();
        }
        String head = String.join(", ", parts.subList(0, parts.size() - 1));
        return head + " and " + parts.getLast();
    }
}
