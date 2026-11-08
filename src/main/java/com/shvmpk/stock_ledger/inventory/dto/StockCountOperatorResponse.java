package com.shvmpk.stock_ledger.inventory.dto;

import com.shvmpk.stock_ledger.user.entity.User;

import java.util.UUID;

public record StockCountOperatorResponse(
        UUID id,
        String fullName,
        String email
) {
    public static StockCountOperatorResponse from(User user) {
        if (user == null) {
            return null;
        }
        return new StockCountOperatorResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail()
        );
    }
}
