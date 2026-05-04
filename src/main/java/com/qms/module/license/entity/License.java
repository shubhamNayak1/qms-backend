package com.qms.module.license.entity;

import com.qms.common.base.BaseEntity;
import com.qms.module.license.enums.LicenseStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * License — a per-seat token that grants login access to the QMS.
 *
 * Lifecycle:
 *   1. SUPER_ADMIN generates licenses in batches  → status = AVAILABLE
 *   2. SUPER_ADMIN assigns one to a user         → status = ASSIGNED
 *   3. The user can now log in.                  (login gate checks for an
 *                                                  active assignment)
 *   4. SUPER_ADMIN may revoke or it may expire   → status = REVOKED / EXPIRED
 *
 * Code format: QMS-XXXX-XXXX-XXXX where each X is a hex digit (case-insensitive).
 */
@Entity
@Table(
    name = "licenses",
    indexes = {
        @Index(name = "idx_license_code",   columnList = "code", unique = true),
        @Index(name = "idx_license_status", columnList = "status"),
        @Index(name = "idx_license_user",   columnList = "assigned_to_user_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class License extends BaseEntity {

    /** Human-readable license key, e.g. "QMS-A8F2-9C1D-7E40". */
    @Column(name = "code", nullable = false, length = 32)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private LicenseStatus status = LicenseStatus.AVAILABLE;

    /** FK-style reference to users.id — null while the license sits in the pool. */
    @Column(name = "assigned_to_user_id")
    private Long assignedToUserId;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "assigned_by_user_id")
    private Long assignedByUserId;

    /** Optional hard expiry. Null = perpetual until manually revoked. */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoked_by_user_id")
    private Long revokedByUserId;

    @Column(name = "notes", length = 500)
    private String notes;
}
