package com.qms.module.user.entity;

import com.qms.common.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Core User entity.
 *
 * Relationship summary:
 *   User  ←→  Role        (many-to-many — owner side: user_roles table)
 *   Effective permissions = union of all permissions from all assigned roles
 *
 * Security notes:
 *   - passwordHash stores BCrypt digest only — never plain text
 *   - failedLoginAttempts + lockedUntil implement brute-force protection
 *   - refreshTokenHash stores BCrypt hash of the refresh token for rotation
 */
@Entity
@Table(
    name = "users",
    indexes = {
        @Index(name = "idx_users_username",    columnList = "username",    unique = true),
        @Index(name = "idx_users_email",       columnList = "email",       unique = true),
        @Index(name = "idx_users_employee_id", columnList = "employee_id", unique = true),
        @Index(name = "idx_users_department",  columnList = "department"),
        @Index(name = "idx_users_active",      columnList = "is_active")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    // ─── Identity ─────────────────────────────────────────────
    @Column(name = "username", nullable = false, unique = true, length = 80)
    private String username;

    @Column(name = "email", nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    // ─── Profile ──────────────────────────────────────────────
    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;

    @Column(name = "phone", length = 25)
    private String phone;

    @Column(name = "department", length = 100)
    private String department;

    @Column(name = "designation", length = 100)
    private String designation;

    @Column(name = "employee_id", length = 50, unique = true)
    private String employeeId;

    @Column(name = "profile_picture_url", length = 500)
    private String profilePictureUrl;

    // ─── Account state ────────────────────────────────────────
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "is_email_verified", nullable = false)
    @Builder.Default
    private Boolean isEmailVerified = false;

    @Column(name = "failed_login_attempts", nullable = false)
    @Builder.Default
    private Integer failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    /** BCrypt hash of the latest issued refresh token — enables secure rotation */
    @Column(name = "refresh_token_hash", length = 255)
    private String refreshTokenHash;

    @Column(name = "password_reset_token", length = 255)
    private String passwordResetToken;

    @Column(name = "password_reset_token_expiry")
    private LocalDateTime passwordResetTokenExpiry;

    // ─── Roles (Many-to-Many owner side) ──────────────────────
    /**
     * A user can hold multiple roles.
     * EAGER is intentional — roles + their permissions are always needed for security checks.
     */
    @ManyToMany(fetch = FetchType.EAGER,
                cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
        name = "user_roles",
        joinColumns        = @JoinColumn(name = "user_id", referencedColumnName = "id"),
        inverseJoinColumns = @JoinColumn(name = "role_id", referencedColumnName = "id")
    )
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    // ─── Computed helpers ─────────────────────────────────────
    public String getFullName() {
        return firstName + " " + lastName;
    }

    /** Flat set of all permission names across all assigned roles */
    public Set<String> getAllPermissions() {
        return roles.stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::getName)
                .collect(Collectors.toSet());
    }

    public boolean isAccountLocked() {
        return lockedUntil != null && LocalDateTime.now().isBefore(lockedUntil);
    }

    // ─── Role management helpers ───────────────────────────────
    public void addRole(Role role) {
        this.roles.add(role);
        role.getUsers().add(this);
    }

    public void removeRole(Role role) {
        this.roles.remove(role);
        role.getUsers().remove(this);
    }

    public void clearRoles() {
        roles.forEach(r -> r.getUsers().remove(this));
        this.roles.clear();
    }
}
