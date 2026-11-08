package com.shvmpk.stock_ledger.order.dto;

import java.util.List;

public record CsvImportResult(
        int totalRows,
        int importedOrders,
        int waitingForStockOrders,
        int failedRows,
        List<CsvRowError> errors
) {
    public record CsvRowError(
            int rowNumber,
            String orderNumber,
            String sku,
            String error
    ) {
    }
}
