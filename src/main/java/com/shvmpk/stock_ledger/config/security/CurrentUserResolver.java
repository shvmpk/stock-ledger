package com.shvmpk.stock_ledger.config.security;

import com.shvmpk.stock_ledger.common.error.ResourceNotFoundException;
import com.shvmpk.stock_ledger.user.entity.User;
import com.shvmpk.stock_ledger.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class CurrentUserResolver {

    private final UserRepository userRepository;

    public User requireCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (isUnauthenticated(authentication)) {
            throw new BadCredentialsException("Not authenticated");
        }

        String email = authentication.getName();
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
    }

    public UUID currentUserIdOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (isUnauthenticated(authentication)) {
            return null;
        }

        String email = authentication.getName();
        if (email == null || email.isBlank()) {
            return null;
        }

        log.debug("Resolving actor for authenticated principal '{}'.", email);

        return userRepository.findByEmailIgnoreCase(email)
                .map(user -> {
                    log.debug("Resolved principal '{}' to user {}.", email, user.getId());
                    return user.getId();
                })
                .orElseGet(() -> {
                    log.debug(
                            "No user row found for authenticated principal '{}'; actor attribution will be null.",
                            email
                    );
                    return null;
                });
    }

    private boolean isUnauthenticated(Authentication authentication) {
        return authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal());
    }
}
