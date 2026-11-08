package com.shvmpk.stock_ledger.integration.service;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import com.shvmpk.stock_ledger.common.error.LastOwnerProtectedException;
import com.shvmpk.stock_ledger.support.AuthEventTestSupport;
import com.shvmpk.stock_ledger.user.dto.CreateUserRequest;
import com.shvmpk.stock_ledger.user.dto.UpdateUserRequest;
import com.shvmpk.stock_ledger.user.entity.AuthEvent;
import com.shvmpk.stock_ledger.user.entity.AuthEventType;
import com.shvmpk.stock_ledger.user.entity.Role;
import com.shvmpk.stock_ledger.user.entity.User;
import com.shvmpk.stock_ledger.user.repository.AuthEventRepository;
import com.shvmpk.stock_ledger.user.repository.UserRepository;
import com.shvmpk.stock_ledger.user.service.UserAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
@DisplayName("User Admin Audit Trail Integration Tests")
class UserAdminServiceAuditTest {

    @Autowired
    private UserAdminService userAdminService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthEventRepository authEventRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

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
    @DisplayName("Creating a user records USER_CREATED with actor and subject")
    void shouldAuditUserCreation() {
        userAdminService.create(new CreateUserRequest(
                "astrid.lindqvist@nordicliving.se",
                "Astrid Lindqvist",
                "WarehousePass2026!",
                Role.STAFF
        ), owner.getId());

        AuthEvent event = singleEvent(AuthEventType.USER_CREATED);
        assertThat(event.getActorUserId()).isEqualTo(owner.getId());
        assertThat(event.getActorEmail()).isEqualTo("ingrid.bergstrom@nordicliving.se");
        assertThat(event.getSubjectEmail()).isEqualTo("astrid.lindqvist@nordicliving.se");
    }

    @Test
    @DisplayName("Deactivating a user records USER_DEACTIVATED")
    void shouldAuditDeactivation() {
        userAdminService.deactivate(staff.getId(), owner.getId());

        AuthEvent event = singleEvent(AuthEventType.USER_DEACTIVATED);
        assertThat(event.getActorUserId()).isEqualTo(owner.getId());
        assertThat(event.getSubjectUserId()).isEqualTo(staff.getId());
    }

    @Test
    @DisplayName("Reactivating a user records USER_REACTIVATED")
    void shouldAuditReactivation() {
        userAdminService.deactivate(staff.getId(), owner.getId());
        userAdminService.update(staff.getId(), new UpdateUserRequest(null, null, true), owner.getId());

        AuthEvent event = singleEvent(AuthEventType.USER_REACTIVATED);
        assertThat(event.getSubjectUserId()).isEqualTo(staff.getId());
    }

    @Test
    @DisplayName("Changing a role records ROLE_CHANGED with the from/to transition")
    void shouldAuditRoleChange() {
        userAdminService.update(staff.getId(), new UpdateUserRequest(null, Role.OWNER, null), owner.getId());

        AuthEvent event = singleEvent(AuthEventType.ROLE_CHANGED);
        assertThat(event.getActorUserId()).isEqualTo(owner.getId());
        assertThat(event.getSubjectUserId()).isEqualTo(staff.getId());
        assertThat(event.getDetails())
                .containsEntry("fromRole", "STAFF")
                .containsEntry("toRole", "OWNER");
    }

    @Test
    @DisplayName("A no-op update writes no audit event")
    void shouldNotAuditNoOpUpdate() {
        userAdminService.update(staff.getId(), new UpdateUserRequest("Lars Eriksson", Role.STAFF, null), owner.getId());

        assertThat(authEventRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("A rejected last-owner demotion writes no audit event")
    void shouldNotAuditRejectedGuard() {
        assertThatThrownBy(() -> userAdminService.update(
                owner.getId(),
                new UpdateUserRequest(null, Role.STAFF, null),
                owner.getId()
        )).isInstanceOf(LastOwnerProtectedException.class);

        assertThat(authEventRepository.findAll()).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("A failure after the audit write rolls back both the role change and the event")
    void shouldRollBackAuditWithTheMutation() {
        try {
            assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
                userAdminService.update(
                        staff.getId(),
                        new UpdateUserRequest(null, Role.OWNER, null),
                        owner.getId()
                );
                throw new IllegalStateException("Forced failure after the audit write");
            })).isInstanceOf(IllegalStateException.class);

            assertThat(userRepository.findById(staff.getId()).orElseThrow().getRole())
                    .as("The role change must roll back with the audit row")
                    .isEqualTo(Role.STAFF);
            assertThat(authEventRepository.findAll()).isEmpty();
        } finally {
            AuthEventTestSupport.clear(jdbcTemplate);
            userRepository.deleteAll();
        }
    }

    private AuthEvent singleEvent(AuthEventType type) {
        List<AuthEvent> events = authEventRepository.findAll().stream()
                .filter(event -> event.getEventType() == type)
                .toList();
        assertThat(events).as("Expected exactly one %s event", type).hasSize(1);
        return events.getFirst();
    }
}
