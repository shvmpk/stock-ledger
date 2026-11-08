package com.shvmpk.stock_ledger.common.error;

import com.shvmpk.stock_ledger.inventory.entity.StockCountStatus;
import org.springframework.http.HttpStatus;

public class InvalidStockCountStateException extends DomainException {

    public InvalidStockCountStateException(StockCountStatus currentStatus) {
        super(
                HttpStatus.BAD_REQUEST,
                "INVALID_STOCK_COUNT_STATE",
                String.format(
                        "Stock count session is in status '%s'. Only IN_PROGRESS sessions can be modified.",
                        currentStatus
                )
        );
    }
}
