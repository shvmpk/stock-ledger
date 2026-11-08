package com.shvmpk.stock_ledger.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        String currentPassword,

        @NotBlank(message = "New password is required")
        @Size(max = 72, message = "Password must be at most 72 characters")
        String newPassword
) {
}
