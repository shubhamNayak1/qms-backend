package com.qms.module.qms.common.entity;

import com.qms.common.base.BaseEntity;
import com.qms.common.enums.QmsRecordType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * QmsLineItem — a single "Existing System / Proposed System / Justification"
 * row attached to a QMS record.
 *
 * Modelled as a generic child keyed by (recordType, recordId) rather than a
 * physical FK, because each QMS sub-module owns its own table (TABLE_PER_CLASS
 * inheritance) so a single FK column would have nothing concrete to reference.
 *
 * Used uniformly across all 5 modules (CAPA, Deviation, Incident,
 * Change Control, Market Complaint) — each module's create/update flow may
 * include any number of these rows.
 */
@Entity
@Table(
    name = "qms_line_items",
    indexes = {
        @Index(name = "idx_qli_record",
               columnList = "record_type,record_id"),
        @Index(name = "idx_qli_record_id",
               columnList = "record_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QmsLineItem extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "record_type", nullable = false, length = 30)
    private QmsRecordType recordType;

    @Column(name = "record_id", nullable = false)
    private Long recordId;

    /** Display order within the record. Auto-assigned by the service. */
    @Column(name = "sr_no", nullable = false)
    private Integer srNo;

    @Column(name = "existing_system", columnDefinition = "TEXT")
    private String existingSystem;

    @Column(name = "proposed_system", columnDefinition = "TEXT")
    private String proposedSystem;

    @Column(name = "justification", columnDefinition = "TEXT")
    private String justification;

    /** Resolved at creation time — denormalised so we can render historical
     *  rows even if the proposing user is later renamed/deleted. */
    @Column(name = "proposed_by_id")
    private Long proposedById;

    @Column(name = "proposed_by_name", length = 150)
    private String proposedByName;

    @Column(name = "proposed_date")
    private LocalDate proposedDate;

    /** Verification phase — after the change is implemented. */
    @Column(name = "status", length = 30)
    private String status;     // e.g. PENDING / IN_PROGRESS / COMPLETED

    @Column(name = "remark", columnDefinition = "TEXT")
    private String remark;

    @Column(name = "checked_by_id")
    private Long checkedById;

    @Column(name = "checked_by_name", length = 150)
    private String checkedByName;
}
