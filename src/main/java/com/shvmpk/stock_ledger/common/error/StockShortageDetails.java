package com.shvmpk.stock_ledger.common.error;

import java.util.List;
import java.util.Map;

/**
 * The figures behind a stock refusal, plus the orders holding reservations
 * and the actions an operator can take next.
 */
public record StockShortageDetails(
        String sku,
        int requestedQuantity,
        int availableQuantity,
        int onHandQuantity,
        int reservedQuantity,
        List<ReservationHolder> reservedBy,
        List<StockAction> actions
) {

    public StockShortageDetails {
        reservedBy = reservedBy == null ? List.of() : List.copyOf(reservedBy);
        actions = actions == null ? List.of() : List.copyOf(actions);
    }

    public Map<String, Object> asExtensions() {
        return Map.of(
                "sku", sku,
                "requestedQuantity", requestedQuantity,
                "availableQuantity", availableQuantity,
                "onHandQuantity", onHandQuantity,
                "reservedQuantity", reservedQuantity,
                "reservedBy", reservedBy,
                "actions", actions
        );
    }
}
