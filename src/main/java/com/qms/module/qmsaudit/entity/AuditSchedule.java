package com.qms.module.qmsaudit.entity;

import com.qms.common.base.BaseEntity;
import com.qms.module.qmsaudit.enums.AuditScheduleStatus;
import com.qms.module.qmsaudit.enums.AuditType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * A planned or completed QMS audit — internal, external, supplier, or regulatory.
 *
 * Lifecycle:  PLANNED → IN_PROGRESS → COMPLETED
 *                                   → CANCELLED (from any state)
 */
@Entity
@Table(
    name = "qms_audit_schedule",
    indexes = {
        @Index(name = "idx_qms_audit_type",    columnList = "audit_type"),
        @Index(name = "idx_qms_audit_status",  columnList = "status"),
        @Index(name = "idx_qms_audit_sched",   columnList = "scheduled_date"),
        @Index(name = "idx_qms_audit_number",  columnList = "record_number", unique = true)
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditSchedule extends BaseEntity {

    @Column(name = "record_number", nullable = false, unique = true, length = 30)
    private String recordNumber;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "audit_type", nullable = false, length = 30)
    private AuditType auditType;

    @Column(name = "scope", columnDefinition = "TEXT")
    private String scope;

    // ── Lead Auditor ──────────────────────────────────────────
    @Column(name = "lead_auditor_id")
    private Long leadAuditorId;

    @Column(name = "lead_auditor_name", length = 150)
    private String leadAuditorName;

    // ── Dates ─────────────────────────────────────────────────
    @Column(name = "scheduled_date")
    private LocalDate scheduledDate;

    @Column(name = "completed_date")
    private LocalDate completedDate;

    // ── Findings & Observations ───────────────────────────────
    @Column(name = "findings", columnDefinition = "TEXT")
    private String findings;

    @Column(name = "observations", columnDefinition = "TEXT")
    private String observations;

    // ── Workflow ──────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private AuditScheduleStatus status = AuditScheduleStatus.PLANNED;
}
