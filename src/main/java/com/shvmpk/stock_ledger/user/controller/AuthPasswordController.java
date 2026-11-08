package com.shvmpk.stock_ledger.user.controller;

import com.shvmpk.stock_ledger.common.api.ApiResponse;
import com.shvmpk.stock_ledger.config.security.CurrentUserResolver;
import com.shvmpk.stock_ledger.config.security.PasswordVerificationWindow;
import com.shvmpk.stock_ledger.user.dto.ChangePasswordRequest;
import com.shvmpk.stock_ledger.user.dto.UserResponse;
import com.shvmpk.stock_ledger.user.entity.User;
import com.shvmpk.stock_ledger.user.service.PasswordService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/password")
@RequiredArgsConstructor
public class AuthPasswordController {

    private final CurrentUserResolver currentUserResolver;
    private final PasswordService passwordService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserResponse>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest
    ) {
        User user = currentUserResolver.requireCurrentUser();
        boolean forcedChangeFresh = PasswordVerificationWindow.isFresh(httpRequest.getSession(false));

        UserResponse response = passwordService.changeOwnPassword(
                user,
                request.currentPassword(),
                request.newPassword(),
                forcedChangeFresh
        );
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
