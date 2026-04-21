package com.qms.module.lms.entity;

import com.qms.module.lms.enums.CertificateStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Training completion certificate issued to a user when an enrollment reaches COMPLETED status.
 *
 * One certificate per enrollment. If the program is retaken after EXPIRED, a new
 * enrollment (and certificate) is created; the old one is kept for audit history.
 *
 * The certificate number follows the format: CERT-{programCode}-{userId}-{YYYYMM}
 *
 * Relationships:
 *   One TrainingCertificate → One Enrollment (1:1)
 */
@Entity
@Table(
    name = "lms_certificates",
    indexes = {
        @Index(name = "idx_cert_enrollment", columnList = "enrollment_id"),
        @Index(name = "idx_cert_user",        columnList = "user_id"),
        @Index(name = "idx_cert_program",     columnList = "program_id"),
        @Index(name = "idx_cert_expiry",      columnList = "expiry_date"),
        @Index(name = "idx_cert_status",      columnList = "status"),
        @Index(name = "idx_cert_number",      columnList = "certificate_number", unique = true)
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TrainingCertificate {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enrollment_id", nullable = false, updatable = false, unique = true)
    private Enrollment enrollment;

    /** Denormalised for fast queries without joins. */
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "user_name", length = 150)
    private String userName;

    @Column(name = "program_id", nullable = false, updatable = false)
    private Long programId;

    @Column(name = "program_title", length = 255)
    private String programTitle;

    @Column(name = "program_code", length = 30)
    private String programCode;

    // ── Certificate identity ─────────────────────────────────

    @Column(name = "certificate_number", nullable = false, unique = true, length = 60)
    private String certificateNumber;

    @Column(name = "issuer", length = 200)
    private String issuer;

    // ── Validity ─────────────────────────────────────────────

    @Column(name = "issued_date", nullable = false)
    private LocalDate issuedDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private CertificateStatus status = CertificateStatus.ACTIVE;

    /** Score achieved on the final assessment attempt (null if no assessment). */
    @Column(name = "score_achieved")
    private Integer scoreAchieved;

    // ── Revocation (if status = REVOKED) ─────────────────────

    @Column(name = "revoked_reason", length = 500)
    private String revokedReason;

    @Column(name = "revoked_by", length = 100)
    private String revokedBy;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist  private void onCreate() { createdAt = LocalDateTime.now(); }
    @PreUpdate   private void onUpdate() { updatedAt = LocalDateTime.now(); }

    public boolean isExpired() {
        return expiryDate != null && LocalDate.now().isAfter(expiryDate);
    }
}
