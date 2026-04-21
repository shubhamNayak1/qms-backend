package com.qms.module.user.entity;

import com.qms.common.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

/**
 * Fine-grained permission — represents a single action on a resource.
 * Convention:  MODULE_ACTION   e.g.  USER_CREATE,  CAPA_APPROVE,  DOCUMENT_DELETE
 *
 * Permissions are assigned to Roles (many-to-many via role_permissions).
 * A user inherits all permissions of all their assigned roles.
 */
@Entity
@Table(
    name = "permissions",
    uniqueConstraints = @UniqueConstraint(name = "uq_permission_name", columnNames = "name")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Permission extends BaseEntity {

    /** e.g. USER_CREATE — must be upper-snake-case, no spaces */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** Human-readable label — shown in admin UI */
    @Column(name = "display_name", nullable = false, length = 150)
    private String displayName;

    /** Groups permissions for UI display: USER, CAPA, DMS, LMS, REPORT */
    @Column(name = "module", nullable = false, length = 50)
    private String module;

    /** Optional description of what this permission allows */
    @Column(name = "description", length = 255)
    private String description;

    /** Back-reference — which roles carry this permission (read-only side) */
    @ManyToMany(mappedBy = "permissions", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<Role> roles = new HashSet<>();
}
