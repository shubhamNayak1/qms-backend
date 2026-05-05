package com.qms.module.qms.common.entity;

import com.qms.common.base.BaseEntity;
import com.qms.common.enums.QmsRecordType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * QmsDepartmentAttachment — one row per (record, department) where a
 * department has been asked to upload a supporting attachment that Head QA
 * must approve before the record can close.
 *
 * Backs the Deviation flow's {@code PENDING_ATTACHMENTS} stage:
 *   1. QA Reviewer / Head QA invites a department to upload (status PENDING).
 *   2. The dept HOD pastes their DMS reference + note (still PENDING).
 *   3. Head QA flips the row APPROVED (or REJECTED with a note).
 *   4. Closure is gated until every row reaches APPROVED.
 *
 * Polymorphic on {@code recordType + recordId} so the same table serves
 * any QMS module that adopts the dept-attachment-approval pattern later
 * (Change Control / Market Complaint can reuse).
 */
@Entity
@Table(
    name = "qms_department_attachments",
    indexes = {
        @Index(name = "idx_qda_record",
               columnList = "record_type,record_id"),
        @Index(name = "idx_qda_record_status",
               columnList = "record_type,record_id,status"),
        @Index(name = "idx_qda_dept",   columnList = "department_id"),
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QmsDepartmentAttachment extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "record_type", nullable = false, length = 30)
    private QmsRecordType recordType;

    @Column(name = "record_id", nullable = false)
    private Long recordId;

    /** FK-style reference to departments.id. */
    @Column(name = "department_id", nullable = false)
    private Long departmentId;

    /** Denormalised name so audit trail survives a department rename. */
    @Column(name = "department_name", length = 150)
    private String departmentName;

    /** DMS file reference (id / number / URL — whatever the upload helper returns). */
    @Column(name = "attachment_ref", length = 255)
    private String attachmentRef;

    @Column(name = "attachment_note", columnDefinition = "TEXT")
    private String attachmentNote;

    /** PENDING / APPROVED / REJECTED. */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "decided_by_id")
    private Long decidedById;

    @Column(name = "decided_by_name", length = 150)
    private String decidedByName;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "decision_note", columnDefinition = "TEXT")
    private String decisionNote;
}
