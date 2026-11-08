package com.shvmpk.stock_ledger.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "password_changed_at", nullable = false)
    private OffsetDateTime passwordChangedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static User create(UUID id, String email, String passwordHash, String fullName, Role role) {
        User user = new User();
        user.id = Objects.requireNonNullElseGet(id, UUID::randomUUID);
        user.email = normalizeEmail(email);
        user.passwordHash = requirePasswordHash(passwordHash);
        user.fullName = requireFullName(fullName);
        user.role = requireRole(role);
        user.active = true;
        user.mustChangePassword = false;
        user.createdAt = OffsetDateTime.now();
        user.passwordChangedAt = user.createdAt;
        user.updatedAt = user.createdAt;
        return user;
    }

    /**
     * Owner-issued credentials are temporary by definition: the account cannot
     * use the application until the owner's password has been replaced.
     */
    public static User createTemporary(UUID id, String email, String passwordHash, String fullName, Role role) {
        User user = create(id, email, passwordHash, fullName, role);
        user.mustChangePassword = true;
        return user;
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cannot be blank");
        }
        return email.trim().toLowerCase();
    }

    private static String requireFullName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Full name cannot be blank");
        }
        return fullName.trim();
    }

    private static Role requireRole(Role role) {
        if (role == null) {
            throw new IllegalArgumentException("Role cannot be null");
        }
        return role;
    }

    private static String requirePasswordHash(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("Password hash cannot be blank");
        }
        return passwordHash;
    }

    public boolean isOwner() {
        return this.role == Role.OWNER;
    }

    public boolean isStaff() {
        return this.role == Role.STAFF;
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = OffsetDateTime.now();
    }

    public void activate() {
        this.active = true;
        this.updatedAt = OffsetDateTime.now();
    }

    public void changePassword(String newPasswordHash) {
        applyPassword(newPasswordHash);
        this.mustChangePassword = false;
    }

    /**
     * An owner reset always produces a temporary credential. The account must
     * choose its own password before using the application again.
     */
    public void resetPassword(String newPasswordHash) {
        applyPassword(newPasswordHash);
        this.mustChangePassword = true;
    }

    private void applyPassword(String newPasswordHash) {
        this.passwordHash = requirePasswordHash(newPasswordHash);
        this.passwordChangedAt = OffsetDateTime.now();
        this.updatedAt = this.passwordChangedAt;
    }

    public void updateFullName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Full name cannot be blank");
        }
        this.fullName = fullName.trim();
        this.updatedAt = OffsetDateTime.now();
    }

    public void updateRole(Role role) {
        this.role = Objects.requireNonNull(role, "Role cannot be null");
        this.updatedAt = OffsetDateTime.now();
    }
}
