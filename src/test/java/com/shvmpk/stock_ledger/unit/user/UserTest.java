package com.shvmpk.stock_ledger.unit.user;

import com.shvmpk.stock_ledger.user.entity.Role;
import com.shvmpk.stock_ledger.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

@DisplayName("User Domain Model Unit Tests")
class UserTest {

    @Test
    @DisplayName("Should successfully construct an active OWNER user with realistic attributes")
    void shouldCreateOwnerUser() {
        UUID id = UUID.randomUUID();
        User user = User.create(
                id,
                "elena.rostova@nordicapparel.com",
                "$2a$12$e8Yk2M83Hl87V6o3...",
                "Elena Rostova",
                Role.OWNER
        );

        assertThat(user.getId()).isEqualTo(id);
        assertThat(user.getEmail()).isEqualTo("elena.rostova@nordicapparel.com");
        assertThat(user.getFullName()).isEqualTo("Elena Rostova");
        assertThat(user.getRole()).isEqualTo(Role.OWNER);
        assertThat(user.isActive()).isTrue();
        assertThat(user.isOwner()).isTrue();
        assertThat(user.isStaff()).isFalse();
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should successfully construct an active STAFF user")
    void shouldCreateStaffUser() {
        UUID id = UUID.randomUUID();
        User user = User.create(
                id,
                "marcus.lindqvist@nordicapparel.com",
                "$2a$12$9876543210...",
                "Marcus Lindqvist",
                Role.STAFF
        );

        assertThat(user.getRole()).isEqualTo(Role.STAFF);
        assertThat(user.isOwner()).isFalse();
        assertThat(user.isStaff()).isTrue();
    }

    @Test
    @DisplayName("Should allow deactivating and reactivating a user")
    void shouldToggleActiveStatus() {
        User user = User.create(
                UUID.randomUUID(),
                "staff.temp@nordicapparel.com",
                "hashed_pw",
                "Temp Staff",
                Role.STAFF
        );

        user.deactivate();
        assertThat(user.isActive()).isFalse();

        user.activate();
        assertThat(user.isActive()).isTrue();
    }

    @Test
    @DisplayName("Should reject invalid email or blank name")
    void shouldValidateInvariants() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> User.create(id, null, "pw", "Name", Role.STAFF))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email cannot be blank");

        assertThatThrownBy(() -> User.create(id, "test@test.com", "pw", "   ", Role.STAFF))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Full name cannot be blank");

        assertThatThrownBy(() -> User.create(id, "test@test.com", "pw", "Name", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Role cannot be null");
    }

    @Test
    @DisplayName("Owner-created and temporary users carry the forced-change flag correctly")
    void shouldTrackTemporaryPasswords() {
        User permanent = User.create(
                UUID.randomUUID(),
                "marcus.lindqvist@nordicapparel.com",
                "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy",
                "Marcus Lindqvist",
                Role.STAFF
        );
        assertThat(permanent.isMustChangePassword()).isFalse();
        assertThat(permanent.getPasswordChangedAt()).isEqualTo(permanent.getCreatedAt());

        User temporary = User.createTemporary(
                UUID.randomUUID(),
                "astrid.lindqvist@nordicapparel.com",
                "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy",
                "Astrid Lindqvist",
                Role.STAFF
        );
        assertThat(temporary.isMustChangePassword()).isTrue();
    }

    @Test
    @DisplayName("Changing a password clears the forced-change flag and stamps the change time")
    void shouldClearFlagOnPasswordChange() {
        // createTemporary and changePassword both stamp OffsetDateTime.now().
        // On a max-resolution host clock the two calls can quantize to the
        // same microsecond, so the real clock is pinned to two separated
        // instants to keep the strictly-after guarantee deterministic.
        OffsetDateTime creationStamp = OffsetDateTime.now().minusSeconds(30);
        OffsetDateTime changeStamp = creationStamp.plusSeconds(60);

        try (MockedStatic<OffsetDateTime> clock = mockStatic(OffsetDateTime.class, CALLS_REAL_METHODS)) {
            clock.when(OffsetDateTime::now).thenReturn(creationStamp, changeStamp);

            User user = User.createTemporary(
                    UUID.randomUUID(),
                    "astrid.lindqvist@nordicapparel.com",
                    "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy",
                    "Astrid Lindqvist",
                    Role.STAFF
            );
            var previousStamp = user.getPasswordChangedAt();

            user.changePassword("$2a$10$7EqJtq98hPqEX7fNZaFWoO5z9nJ7eKx6h1Yw4mQ0Wc2sB8uT3vR1a");

            assertThat(user.getPasswordHash())
                    .isEqualTo("$2a$10$7EqJtq98hPqEX7fNZaFWoO5z9nJ7eKx6h1Yw4mQ0Wc2sB8uT3vR1a");
            assertThat(user.isMustChangePassword()).isFalse();
            assertThat(user.getPasswordChangedAt()).isAfter(previousStamp);
            assertThat(user.getUpdatedAt()).isEqualTo(user.getPasswordChangedAt());
        }
    }

    @Test
    @DisplayName("An owner reset flags the account as requiring a change")
    void shouldFlagAccountOnOwnerReset() {
        User user = User.create(
                UUID.randomUUID(),
                "astrid.lindqvist@nordicapparel.com",
                "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy",
                "Astrid Lindqvist",
                Role.STAFF
        );

        user.resetPassword("$2a$10$7EqJtq98hPqEX7fNZaFWoO5z9nJ7eKx6h1Yw4mQ0Wc2sB8uT3vR1a");

        assertThat(user.isMustChangePassword()).isTrue();
        assertThat(user.getPasswordChangedAt()).isAfter(user.getCreatedAt().minusSeconds(1));
    }
}
