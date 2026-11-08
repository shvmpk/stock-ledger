package com.shvmpk.stock_ledger.user.service;

import com.shvmpk.stock_ledger.common.error.CurrentPasswordRequiredException;
import com.shvmpk.stock_ledger.common.error.InvalidCurrentPasswordException;
import com.shvmpk.stock_ledger.common.error.PasswordReuseException;
import com.shvmpk.stock_ledger.common.error.ResourceNotFoundException;
import com.shvmpk.stock_ledger.common.error.SelfPasswordResetNotAllowedException;
import com.shvmpk.stock_ledger.common.error.WeakPasswordException;
import com.shvmpk.stock_ledger.user.dto.UserResponse;
import com.shvmpk.stock_ledger.user.entity.User;
import com.shvmpk.stock_ledger.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PasswordService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final AuthEventRecorder authEventRecorder;
    private final AuthMetrics authMetrics;

    /**
     * @param forcedChangeFresh true only when this session proved the current
     *                          password less than ten minutes ago, which lets a
     *                          forced change omit {@code currentPassword}.
     */
    @Transactional
    public UserResponse changeOwnPassword(
            User user,
            String currentPassword,
            String newPassword,
            boolean forcedChangeFresh
    ) {
        if (currentPassword == null || currentPassword.isBlank()) {
            if (!user.isMustChangePassword() || !forcedChangeFresh) {
                throw new CurrentPasswordRequiredException();
            }
        } else if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidCurrentPasswordException();
        }

        validatePolicy(newPassword, user, "newPassword");

        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new PasswordReuseException();
        }

        user.changePassword(passwordEncoder.encode(newPassword));
        User saved = userRepository.save(user);
        authEventRecorder.recordPasswordChanged(saved);
        authMetrics.passwordChanged();
        return UserResponse.from(saved);
    }

    @Transactional
    public UserResponse resetByOwner(User owner, UUID targetUserId, String newPassword) {
        if (owner.getId().equals(targetUserId)) {
            throw new SelfPasswordResetNotAllowedException();
        }

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", targetUserId));

        validatePolicy(newPassword, target, "newPassword");

        target.resetPassword(passwordEncoder.encode(newPassword));
        User saved = userRepository.save(target);
        authEventRecorder.recordPasswordReset(owner, saved);
        authMetrics.passwordReset();
        return UserResponse.from(saved);
    }

    private void validatePolicy(String newPassword, User user, String field) {
        List<PasswordViolation> violations = passwordPolicy.validate(
                newPassword,
                user.getEmail(),
                user.getFullName()
        );
        if (!violations.isEmpty()) {
            throw new WeakPasswordException(field, violations.stream()
                    .map(PasswordViolation::message)
                    .toList());
        }
    }
}
