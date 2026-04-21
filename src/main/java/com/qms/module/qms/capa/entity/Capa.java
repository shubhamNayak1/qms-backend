package com.qms.module.qms.capa.entity;

import com.qms.common.enums.QmsRecordType;
import com.qms.module.qms.common.entity.QmsRecord;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * Corrective And Preventive Action record.
 *
 * Extends QmsRecord for all shared fields (status, priority, assignment,
 * dates, workflow history). Only CAPA-specific fields are defined here.
 */
@Entity
@Table(
    name = "qms_capa",
    indexes = {
        @Index(name = "idx_capa_status",   columnList = "status"),
        @Index(name = "idx_capa_priority", columnList = "priority"),
        @Index(name = "idx_capa_assigned", columnList = "assigned_to_id"),
        @Index(name = "idx_capa_number",   columnList = "record_number", unique = true),
        @Index(name = "idx_capa_due",      columnList = "due_date")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Capa extends QmsRecord {

    /** Origin of the CAPA — what triggered it. */
    @Column(name = "source", length = 100)
    private String source;  // Audit, Complaint, Deviation, Inspection, Internal

    /** CAPA type classification. */
    @Column(name = "capa_type", length = 80)
    private String capaType;  // Corrective / Preventive / Both

    /** Description of preventive measures taken. */
    @Column(name = "preventive_action", columnDefinition = "TEXT")
    private String preventiveAction;

    /** Date on which the effectiveness of the CAPA will be checked. */
    @Column(name = "effectiveness_check_date")
    private LocalDate effectivenessCheckDate;

    /** Result of the effectiveness verification. */
    @Column(name = "effectiveness_result", columnDefinition = "TEXT")
    private String effectivenessResult;

    /** Whether the CAPA was verified as effective. */
    @Column(name = "is_effective")
    private Boolean isEffective;

    /** Reference to a linked Deviation record (if this CAPA arose from one). */
    @Column(name = "linked_deviation_number", length = 30)
    private String linkedDeviationNumber;

    @PrePersist
    private void prePersist() {
        setRecordType(QmsRecordType.CAPA);
    }
}
