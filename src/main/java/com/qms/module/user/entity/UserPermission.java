package com.qms.module.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * UserPermission — per-user override of permissions on top of the
 * role-based grants.
 *
 *   effective permissions = (role permissions)
 *                         ∪ (user grants)
 *                         \ (user revokes)
 *
 * One row per (user, permission) pair; `grantType` tells us which
 * direction. Keeping both GRANT and REVOKE on the same table lets us
 * audit a user's full permission history without joining two tables.
 */
@Entity
@Table(name = "user_permissions")
@IdClass(UserPermission.PK.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPermission {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Id
    @Column(name = "permission_id", nullable = false)
    private Long permissionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "grant_type", nullable = false, length = 10)
    private GrantType grantType;

    @Column(name = "granted_by", length = 100)
    private String grantedBy;

    @Column(name = "granted_at", nullable = false)
    private LocalDateTime grantedAt;

    @Column(name = "reason", length = 500)
    private String reason;

    public enum GrantType { GRANT, REVOKE }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PK implements Serializable {
        private Long userId;
        private Long permissionId;

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PK that)) return false;
            return Objects.equals(userId, that.userId)
                && Objects.equals(permissionId, that.permissionId);
        }

        @Override public int hashCode() { return Objects.hash(userId, permissionId); }
    }
}
