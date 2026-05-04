package com.qms.module.qms.common.entity;

import com.qms.common.base.BaseEntity;
import com.qms.common.enums.QmsRecordType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * QmsDepartmentComment — a comment recorded by the HOD of a specific
 * department after QA routes a record for cross-functional review.
 *
 * Multiple departments can comment on the same record independently;
 * each row is owned by a department and tracks who completed it and when.
 * Like QmsLineItem this is keyed on (recordType, recordId) rather than a
 * physical FK because of TABLE_PER_CLASS inheritance.
 */
@Entity
@Table(
    name = "qms_department_comments",
    indexes = {
        @Index(name = "idx_qdc_record",
               columnList = "record_type,record_id"),
        @Index(name = "idx_qdc_dept",   columnList = "department_id"),
        @Index(name = "idx_qdc_status", columnList = "status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QmsDepartmentComment extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "record_type", nullable = false, length = 30)
    private QmsRecordType recordType;

    @Column(name = "record_id", nullable = false)
    private Long recordId;

    /** FK-style reference to departments.id. */
    @Column(name = "department_id", nullable = false)
    private Long departmentId;

    /** Denormalised name so historical comments survive a department rename. */
    @Column(name = "department_name", nullable = false, length = 150)
    private String departmentName;

    /** PENDING / COMPLETED / SKIPPED — set by the service when the dept HOD comments. */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "done_by_id")
    private Long doneById;

    @Column(name = "done_by_name", length = 150)
    private String doneByName;

    @Column(name = "done_at")
    private LocalDateTime doneAt;
}
