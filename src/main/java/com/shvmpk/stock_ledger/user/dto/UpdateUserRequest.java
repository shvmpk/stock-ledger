package com.shvmpk.stock_ledger.user.dto;

import com.shvmpk.stock_ledger.user.entity.Role;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Size(max = 150, message = "Full name must be at most 150 characters")
        @Pattern(regexp = ".*\\S.*", message = "Full name must not be blank")
        String fullName,

        Role role,

        Boolean active
) {
    public boolean hasAnyField() {
        return fullName != null || role != null || active != null;
    }
}
