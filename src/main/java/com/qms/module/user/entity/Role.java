package com.qms.module.user.entity;

import com.qms.common.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

/**
 * Role groups a set of Permissions and is assigned to Users.
 *
 * Relationship summary:
 *   Role  ←→  Permission   (many-to-many — owner side: role_permissions table)
 *   Role  ←→  User         (many-to-many — non-owner side, mapped by User.roles)
 */
@Entity
@Table(
    name = "roles",
    uniqueConstraints = @UniqueConstraint(name = "uq_role_name", columnNames = "name")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role extends BaseEntity {

    /** Stored WITHOUT the ROLE_ prefix; Spring Security prefix is added at runtime */
    @Column(name = "name", nullable = false, length = 60)
    private String name;                 // e.g. SUPER_ADMIN, QA_MANAGER

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;          // e.g. Super Administrator

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "is_system_role", nullable = false)
    @Builder.Default
    private Boolean isSystemRole = false; // system roles cannot be deleted

    /**
     * Owning side of Role ↔ Permission many-to-many.
     * The join table is role_permissions(role_id, permission_id).
     */
    @ManyToMany(fetch = FetchType.EAGER,
                cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
        name = "role_permissions",
        joinColumns        = @JoinColumn(name = "role_id",       referencedColumnName = "id"),
        inverseJoinColumns = @JoinColumn(name = "permission_id", referencedColumnName = "id")
    )
    @Builder.Default
    private Set<Permission> permissions = new HashSet<>();

    /** Back-reference — which users carry this role (read-only side) */
    @ManyToMany(mappedBy = "roles", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<User> users = new HashSet<>();

    // ─── helpers ───────────────────────────────────────────────
    public void addPermission(Permission permission) {
        this.permissions.add(permission);
        permission.getRoles().add(this);
    }

    public void removePermission(Permission permission) {
        this.permissions.remove(permission);
        permission.getRoles().remove(this);
    }
}
