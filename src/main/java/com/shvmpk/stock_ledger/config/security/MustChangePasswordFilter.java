package com.shvmpk.stock_ledger.config.security;

import com.shvmpk.stock_ledger.user.entity.User;
import com.shvmpk.stock_ledger.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

/**
 * Blocks every protected endpoint while an owner-issued temporary password is
 * still in use. Only the endpoints needed to finish the change (or leave) stay
 * open. Registered after Spring Security's authorization filter so the
 * SecurityContext is populated.
 */
@RequiredArgsConstructor
public class MustChangePasswordFilter extends OncePerRequestFilter {

    private static final Set<String> ACCOUNT_PATHS = Set.of(
            "/api/v1/auth/password",
            "/api/v1/auth/me",
            "/api/v1/auth/logout",
            "/api/v1/auth/login"
    );

    private final UserRepository userRepository;
    private final ProblemDetailWriter problemDetailWriter;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return ACCOUNT_PATHS.contains(path)
                || path.startsWith("/actuator/")
                || path.startsWith("/api/v1/webhooks/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<User> currentUser = userRepository.findByEmailIgnoreCase(authentication.getName());
        if (currentUser.isPresent() && currentUser.get().isMustChangePassword()) {
            problemDetailWriter.write(
                    response,
                    HttpStatus.FORBIDDEN,
                    "PASSWORD_CHANGE_REQUIRED",
                    "Password Change Required",
                    "Your password needs to be updated before you can continue."
            );
            return;
        }

        filterChain.doFilter(request, response);
    }
}
