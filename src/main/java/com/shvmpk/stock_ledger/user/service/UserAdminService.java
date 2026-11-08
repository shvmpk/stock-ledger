package com.shvmpk.stock_ledger.user.service;

import com.shvmpk.stock_ledger.common.error.CannotDeactivateSelfException;
import com.shvmpk.stock_ledger.common.error.DuplicateResourceException;
import com.shvmpk.stock_ledger.common.error.LastOwnerProtectedException;
import com.shvmpk.stock_ledger.common.error.NoUserChangesException;
import com.shvmpk.stock_ledger.common.error.ResourceNotFoundException;
import com.shvmpk.stock_ledger.common.error.WeakPasswordException;
import com.shvmpk.stock_ledger.user.dto.CreateUserRequest;
import com.shvmpk.stock_ledger.user.dto.UpdateUserRequest;
import com.shvmpk.stock_ledger.user.dto.UserResponse;
import com.shvmpk.stock_ledger.user.entity.Role;
import com.shvmpk.stock_ledger.user.entity.User;
import com.shvmpk.stock_ledger.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class UserAdminService {

    private static final Logger SECURITY_AUDIT = LoggerFactory.getLogger("SECURITY_AUDIT");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final AuthEventRecorder authEventRecorder;

    @Transactional
    public UserResponse create(CreateUserRequest request, UUID currentUserId) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new DuplicateResourceException("User", "email", request.email());
        }

        List<PasswordViolation> violations = passwordPolicy.validate(
                request.password(),
                request.email(),
                request.fullName()
        );
        if (!violations.isEmpty()) {
            throw new WeakPasswordException("password", violations.stream()
                    .map(PasswordViolation::message)
                    .toList());
        }

        User user = User.createTemporary(
                UUID.randomUUID(),
                request.email(),
                passwordEncoder.encode(request.password()),
                request.fullName(),
                request.role()
        );
        User saved = userRepository.save(user);

        withActor(currentUserId, actor -> authEventRecorder.recordUserCreated(actor, saved));
        return UserResponse.from(saved);
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request, UUID currentUserId) {
        if (!request.hasAnyField()) {
            throw new NoUserChangesException();
        }

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));

        boolean deactivating = Boolean.FALSE.equals(request.active());
        boolean demoting = user.isOwner() && request.role() != null && request.role() != Role.OWNER;

        if (deactivating && user.getId().equals(currentUserId)) {
            throw new CannotDeactivateSelfException();
        }

        if (user.isActive()
                && user.isOwner()
                && (deactivating || demoting)
                && userRepository.countByRoleAndActiveTrue(Role.OWNER) <= 1) {
            throw new LastOwnerProtectedException();
        }

        Role previousRole = user.getRole();
        boolean wasActive = user.isActive();

        if (request.fullName() != null) {
            user.updateFullName(request.fullName());
        }
        if (request.role() != null) {
            user.updateRole(request.role());
        }
        if (request.active() != null) {
            if (request.active()) {
                user.activate();
            } else {
                user.deactivate();
            }
        }

        User saved = userRepository.save(user);

        boolean roleChanged = saved.getRole() != previousRole;
        boolean deactivated = wasActive && !saved.isActive();
        boolean reactivated = !wasActive && saved.isActive();

        if (roleChanged) {
            withActor(currentUserId, actor ->
                    authEventRecorder.recordRoleChanged(actor, saved, previousRole, saved.getRole()));
        }
        if (deactivated) {
            withActor(currentUserId, actor -> authEventRecorder.recordUserDeactivated(actor, saved));
        }
        if (reactivated) {
            withActor(currentUserId, actor -> authEventRecorder.recordUserReactivated(actor, saved));
        }

        return UserResponse.from(saved);
    }

    @Transactional
    public UserResponse deactivate(UUID id, UUID currentUserId) {
        return update(id, new UpdateUserRequest(null, null, false), currentUserId);
    }

    /**
     * Administrative changes are always performed by an authenticated owner, so
     * the actor id is normally present. Tests and internal maintenance jobs may
     * call the service without a security context; in that case the audit event
     * is skipped rather than failing the operation.
     */
    private void withActor(UUID actorUserId, Consumer<User> auditAction) {
        if (actorUserId == null) {
            return;
        }
        userRepository.findById(actorUserId)
                .ifPresentOrElse(auditAction, () -> SECURITY_AUDIT.warn(
                        "Skipped an administrative audit event: actor {} no longer exists.", actorUserId));
    }
}
