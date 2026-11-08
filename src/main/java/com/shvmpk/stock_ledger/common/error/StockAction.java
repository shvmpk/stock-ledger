package com.shvmpk.stock_ledger.common.error;

/**
 * A semantic next step attached to a refusal. The code is stable for clients;
 * the label is the operator-facing wording. The frontend decides which codes
 * become links and which are advice only.
 */
public record StockAction(String code, String label) {

    public static final StockAction RECEIVE_STOCK = new StockAction("RECEIVE_STOCK", "Receive more stock");
    public static final StockAction REDUCE_QUANTITY = new StockAction("REDUCE_QUANTITY", "Reduce this order");
    public static final StockAction VIEW_MOVEMENTS = new StockAction("VIEW_MOVEMENTS", "View movements");
    public static final StockAction VIEW_RESERVATIONS = new StockAction("VIEW_RESERVATIONS", "View reservations");
    public static final StockAction VIEW_RETIRED_PRODUCTS =
            new StockAction("VIEW_RETIRED_PRODUCTS", "View retired products");
}
