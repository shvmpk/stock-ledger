package com.shvmpk.stock_ledger.order.dto;

import com.shvmpk.stock_ledger.order.entity.ItemCondition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record ProcessReturnRequest(
        String reason,

        @Size(max = 2000, message = "Operator notes must not exceed 2000 characters")
        String operatorNotes,

        @NotEmpty(message = "Return must contain at least one item")
        List<@Valid ReturnItemRequest> items
) {
    public ProcessReturnRequest(String reason, List<ReturnItemRequest> items) {
        this(reason, null, items);
    }

    public record ReturnItemRequest(
            @NotNull(message = "Order item ID is required")
            UUID orderItemId,

            @NotNull(message = "Quantity is required")
            @Positive(message = "Quantity must be positive")
            Integer quantity,

            @NotNull(message = "Condition is required")
            ItemCondition condition
    ) {
    }
}
