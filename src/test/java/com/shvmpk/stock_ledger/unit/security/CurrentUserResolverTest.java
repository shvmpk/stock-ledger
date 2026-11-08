package com.shvmpk.stock_ledger.unit.security;

import com.shvmpk.stock_ledger.common.error.ResourceNotFoundException;
import com.shvmpk.stock_ledger.config.security.CurrentUserResolver;
import com.shvmpk.stock_ledger.user.entity.Role;
import com.shvmpk.stock_ledger.user.entity.User;
import com.shvmpk.stock_ledger.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CurrentUserResolver Unit Tests")
class CurrentUserResolverTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CurrentUserResolver resolver;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should resolve the user id for an authenticated principal with a user row")
    void shouldResolveCurrentUserId() {
        User user = User.create(
                UUID.randomUUID(),
                "mira.lind@northwind-supply.co",
                "$2a$10$warehousehash",
                "Mira Lind",
                Role.STAFF
        );
        when(userRepository.findByEmailIgnoreCase("mira.lind@northwind-supply.co"))
                .thenReturn(Optional.of(user));
        authenticate("mira.lind@northwind-supply.co");

        assertThat(resolver.currentUserIdOrNull()).isEqualTo(user.getId());
        assertThat(resolver.requireCurrentUser()).isSameAs(user);
    }

    @Test
    @DisplayName("Should return null for a test principal with no matching user row")
    void shouldReturnNullWhenPrincipalHasNoUserRow() {
        when(userRepository.findByEmailIgnoreCase("staff")).thenReturn(Optional.empty());
        authenticate("staff");

        assertThat(resolver.currentUserIdOrNull()).isNull();
    }

    @Test
    @DisplayName("Should reject a missing authentication with 401 BadCredentialsException")
    void shouldRejectMissingAuthentication() {
        assertThatThrownBy(() -> resolver.requireCurrentUser())
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Not authenticated");
    }

    @Test
    @DisplayName("Should reject an authenticated email with no user row")
    void shouldRejectMissingUserRow() {
        when(userRepository.findByEmailIgnoreCase("missing@northwind-supply.co"))
                .thenReturn(Optional.empty());
        authenticate("missing@northwind-supply.co");

        assertThatThrownBy(() -> resolver.requireCurrentUser())
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User");
    }

    private void authenticate(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        email,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_STAFF"))
                )
        );
    }
}
