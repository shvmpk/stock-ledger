package com.shvmpk.stock_ledger.user.service;

import com.shvmpk.stock_ledger.user.entity.AuthFailureReason;
import com.shvmpk.stock_ledger.user.entity.User;
import com.shvmpk.stock_ledger.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Session-based authentication use cases. Existing session mutation stays in
 * the controller; this service owns credential verification and the audit
 * trail so the rules are testable without MockMvc.
 */
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private static final Logger SECURITY_AUDIT = LoggerFactory.getLogger("SECURITY_AUDIT");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthEventRecorder authEventRecorder;
    private final AuthMetrics authMetrics;

    public User login(String email, String rawPassword) {
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);

        if (user == null) {
            authMetrics.loginFailed();
            recordSafely(() -> authEventRecorder.recordLoginFailed(
                    email, null, AuthFailureReason.UNKNOWN_EMAIL));
            throw new BadCredentialsException("Invalid email or password");
        }

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            authMetrics.loginFailed();
            recordSafely(() -> authEventRecorder.recordLoginFailed(
                    email, user, AuthFailureReason.INVALID_PASSWORD));
            throw new BadCredentialsException("Invalid email or password");
        }

        if (!user.isActive()) {
            authMetrics.loginFailed();
            recordSafely(() -> authEventRecorder.recordLoginFailed(
                    email, user, AuthFailureReason.INACTIVE_ACCOUNT));
            throw new BadCredentialsException("User account is inactive");
        }

        authMetrics.loginSucceeded();
        recordSafely(() -> authEventRecorder.recordLoginSucceeded(user));
        return user;
    }

    public void logout(User user) {
        recordSafely(() -> authEventRecorder.recordLogout(user));
    }

    /**
     * Losing a failed-login record must not turn into a login outage. Database
     * failures are already logged by the recorder's transaction boundary; this
     * guard covers the rest.
     */
    private void recordSafely(Runnable auditAction) {
        try {
            auditAction.run();
        } catch (RuntimeException exception) {
            SECURITY_AUDIT.error("Failed to append an authentication audit event.", exception);
        }
    }
}
