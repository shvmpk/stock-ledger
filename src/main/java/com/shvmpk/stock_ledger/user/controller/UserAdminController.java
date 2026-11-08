package com.shvmpk.stock_ledger.user.controller;

import com.shvmpk.stock_ledger.common.api.ApiResponse;
import com.shvmpk.stock_ledger.config.security.CurrentUserResolver;
import com.shvmpk.stock_ledger.user.dto.CreateUserRequest;
import com.shvmpk.stock_ledger.user.dto.ResetPasswordRequest;
import com.shvmpk.stock_ledger.user.dto.UpdateUserRequest;
import com.shvmpk.stock_ledger.user.dto.UserResponse;
import com.shvmpk.stock_ledger.user.entity.User;
import com.shvmpk.stock_ledger.user.repository.UserRepository;
import com.shvmpk.stock_ledger.user.service.PasswordService;
import com.shvmpk.stock_ledger.user.service.UserAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class UserAdminController {

    private final UserRepository userRepository;
    private final UserAdminService userAdminService;
    private final PasswordService passwordService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserResponse>>> listUsers() {
        List<UserResponse> users = userRepository.findAll().stream()
                .map(UserResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(users));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserResponse created = userAdminService.create(request, currentUserResolver.currentUserIdOrNull());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        UserResponse updated = userAdminService.update(
                id,
                request,
                currentUserResolver.currentUserIdOrNull()
        );
        return ResponseEntity.ok(ApiResponse.ok(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> deactivateUser(@PathVariable UUID id) {
        UserResponse deactivated = userAdminService.deactivate(
                id,
                currentUserResolver.currentUserIdOrNull()
        );
        return ResponseEntity.ok(ApiResponse.ok(deactivated));
    }

    @PostMapping("/{id}/password-reset")
    public ResponseEntity<ApiResponse<UserResponse>> resetPassword(
            @PathVariable UUID id,
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        User owner = currentUserResolver.requireCurrentUser();
        UserResponse reset = passwordService.resetByOwner(owner, id, request.newPassword());
        return ResponseEntity.ok(ApiResponse.ok(reset));
    }
}
