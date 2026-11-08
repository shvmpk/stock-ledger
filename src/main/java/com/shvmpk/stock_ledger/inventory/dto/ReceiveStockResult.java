package com.shvmpk.stock_ledger.inventory.dto;

import java.util.List;

public record ReceiveStockResult(
        StockLevelResponse stockLevel,
        List<String> allocatedOrderNumbers
) {
}
