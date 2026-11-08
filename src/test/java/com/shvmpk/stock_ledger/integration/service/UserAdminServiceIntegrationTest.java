package com.shvmpk.stock_ledger.integration.service;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import com.shvmpk.stock_ledger.common.error.CannotDeactivateSelfException;
import com.shvmpk.stock_ledger.common.error.DomainException;
import com.shvmpk.stock_ledger.common.error.LastOwnerProtectedException;
import com.shvmpk.stock_ledger.common.error.NoUserChangesException;
import com.shvmpk.stock_ledger.common.error.ResourceNotFoundException;
import com.shvmpk.stock_ledger.user.dto.UpdateUserRequest;
import com.shvmpk.stock_ledger.user.dto.UserResponse;
import com.shvmpk.stock_ledger.user.entity.Role;
import com.shvmpk.stock_ledger.user.entity.User;
import com.shvmpk.stock_ledger.user.repository.UserRepository;
import com.shvmpk.stock_ledger.user.service.UserAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
@DisplayName("UserAdminService Integration Tests (Update Guards & Reactivation)")
class UserAdminServiceIntegrationTest {

    @Autowired
    private UserAdminService userAdminService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User owner;
    private User staff;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        owner = userRepository.saveAndFlush(User.create(
                UUID.randomUUID(),
                "ingrid.bergstrom@nordicliving.se",
                passwordEncoder.encode("OwnerPass2026!"),
                "Ingrid Bergström",
                Role.OWNER
        ));
        staff = userRepository.saveAndFlush(User.create(
                UUID.randomUUID(),
                "lars.eriksson@nordicliving.se",
                passwordEncoder.encode("StaffPass2026!"),
                "Lars Eriksson",
                Role.STAFF
        ));
    }

    @Test
    @DisplayName("Should update a user's full name and role")
    void shouldUpdateFullNameAndRole() {
        UserResponse updated = userAdminService.update(
                staff.getId(),
                new UpdateUserRequest("Karin Nygård", Role.OWNER, null),
                owner.getId()
        );

        assertThat(updated.fullName()).isEqualTo("Karin Nygård");
        assertThat(updated.role()).isEqualTo(Role.OWNER);
        assertThat(updated.active()).isTrue();

        User reloaded = userRepository.findById(staff.getId()).orElseThrow();
        assertThat(reloaded.getFullName()).isEqualTo("Karin Nygård");
        assertThat(reloaded.getRole()).isEqualTo(Role.OWNER);
        assertThat(reloaded.getUpdatedAt()).isAfterOrEqualTo(staff.getUpdatedAt());
    }

    @Test
    @DisplayName("Should reactivate an inactive staff member")
    void shouldReactivateInactiveStaff() {
        staff.deactivate();
        userRepository.saveAndFlush(staff);

        UserResponse reactivated = userAdminService.update(
                staff.getId(),
                new UpdateUserRequest(null, null, true),
                owner.getId()
        );

        assertThat(reactivated.active()).isTrue();
        assertThat(userRepository.findById(staff.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    @DisplayName("Should reject deactivating the last active owner")
    void shouldRejectDeactivatingLastActiveOwner() {
        assertThatThrownBy(() -> userAdminService.update(
                owner.getId(),
                new UpdateUserRequest(null, null, false),
                staff.getId()
        ))
                .isInstanceOf(LastOwnerProtectedException.class)
                .extracting(ex -> ((DomainException) ex).getErrorCode())
                .isEqualTo("LAST_OWNER_PROTECTED");

        User unchanged = userRepository.findById(owner.getId()).orElseThrow();
        assertThat(unchanged.isActive()).isTrue();
        assertThat(unchanged.getRole()).isEqualTo(Role.OWNER);
    }

    @Test
    @DisplayName("Should reject demoting the last active owner")
    void shouldRejectDemotingLastActiveOwner() {
        assertThatThrownBy(() -> userAdminService.update(
                owner.getId(),
                new UpdateUserRequest(null, Role.STAFF, null),
                staff.getId()
        ))
                .isInstanceOf(LastOwnerProtectedException.class)
                .extracting(ex -> ((DomainException) ex).getErrorCode())
                .isEqualTo("LAST_OWNER_PROTECTED");

        assertThat(userRepository.findById(owner.getId()).orElseThrow().getRole()).isEqualTo(Role.OWNER);
    }

    @Test
    @DisplayName("Should reject self-deactivation")
    void shouldRejectSelfDeactivation() {
        assertThatThrownBy(() -> userAdminService.update(
                owner.getId(),
                new UpdateUserRequest(null, null, false),
                owner.getId()
        ))
                .isInstanceOf(CannotDeactivateSelfException.class)
                .extracting(ex -> ((DomainException) ex).getErrorCode())
                .isEqualTo("CANNOT_DEACTIVATE_SELF");

        assertThat(userRepository.findById(owner.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    @DisplayName("Should reject an update request with no changes")
    void shouldRejectEmptyUpdate() {
        assertThatThrownBy(() -> userAdminService.update(
                staff.getId(),
                new UpdateUserRequest(null, null, null),
                owner.getId()
        ))
                .isInstanceOf(NoUserChangesException.class)
                .extracting(ex -> ((DomainException) ex).getErrorCode())
                .isEqualTo("NO_USER_CHANGES");
    }

    @Test
    @DisplayName("Should allow demoting an owner when another active owner remains")
    void shouldAllowDemotingOwnerWhenAnotherOwnerRemains() {
        User secondOwner = userRepository.saveAndFlush(User.create(
                UUID.randomUUID(),
                "maja.magnusson@nordicliving.se",
                passwordEncoder.encode("OwnerPass2026!"),
                "Maja Magnusson",
                Role.OWNER
        ));

        UserResponse updated = userAdminService.update(
                owner.getId(),
                new UpdateUserRequest(null, Role.STAFF, null),
                secondOwner.getId()
        );

        assertThat(updated.role()).isEqualTo(Role.STAFF);
        assertThat(userRepository.countByRoleAndActiveTrue(Role.OWNER)).isEqualTo(1);
    }

    @Test
    @DisplayName("Should allow deactivating an owner when another active owner remains")
    void shouldAllowDeactivatingOwnerWhenAnotherOwnerRemains() {
        User secondOwner = userRepository.saveAndFlush(User.create(
                UUID.randomUUID(),
                "maja.magnusson@nordicliving.se",
                passwordEncoder.encode("OwnerPass2026!"),
                "Maja Magnusson",
                Role.OWNER
        ));

        UserResponse updated = userAdminService.update(
                owner.getId(),
                new UpdateUserRequest(null, null, false),
                secondOwner.getId()
        );

        assertThat(updated.active()).isFalse();
        assertThat(userRepository.countByRoleAndActiveTrue(Role.OWNER)).isEqualTo(1);
    }

    @Test
    @DisplayName("Should reject an unknown user")
    void shouldRejectUnknownUser() {
        UUID unknownId = UUID.randomUUID();
        assertThatThrownBy(() -> userAdminService.update(
                unknownId,
                new UpdateUserRequest("Karin Nygård", null, null),
                owner.getId()
        ))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(unknownId.toString());
    }

    @Test
    @DisplayName("Should guard DELETE-style deactivation for self and the last owner")
    void shouldGuardDeactivationAlias() {
        assertThatThrownBy(() -> userAdminService.deactivate(owner.getId(), owner.getId()))
                .isInstanceOf(CannotDeactivateSelfException.class)
                .extracting(ex -> ((DomainException) ex).getErrorCode())
                .isEqualTo("CANNOT_DEACTIVATE_SELF");

        assertThatThrownBy(() -> userAdminService.deactivate(owner.getId(), staff.getId()))
                .isInstanceOf(LastOwnerProtectedException.class)
                .extracting(ex -> ((DomainException) ex).getErrorCode())
                .isEqualTo("LAST_OWNER_PROTECTED");
    }
}
