package com.qms.module.qms.deviation.entity;

import com.qms.common.enums.QmsRecordType;
import com.qms.module.qms.common.entity.QmsRecord;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "qms_deviation", indexes = {
        @Index(name = "idx_dev_status",   columnList = "status"),
        @Index(name = "idx_dev_priority", columnList = "priority"),
        @Index(name = "idx_dev_number",   columnList = "record_number", unique = true),
        @Index(name = "idx_dev_assigned", columnList = "assigned_to_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Deviation extends QmsRecord {

    /**
     * Parent Incident this Deviation was raised against. Per Kedar-sir spec
     * every Deviation must originate from an Incident where the HOD ticked
     * deviation_required and QA confirmed it during evaluation. The Initiator
     * picks the parent Incident on the Deviation create form; the form
     * pre-fills product/batch/process-area from the Incident.
     */
    @Column(name = "parent_incident_id")
    private Long parentIncidentId;

    /** Planned (pre-approved) or Unplanned (unexpected occurrence). */
    @Column(name = "deviation_type", length = 80)
    private String deviationType;   // Planned / Unplanned

    /** Affected product batch or lot number. */
    @Column(name = "product_batch", length = 100)
    private String productBatch;

    /** Manufacturing or process area where the deviation occurred. */
    @Column(name = "process_area", length = 100)
    private String processArea;

    /** Assessment of quality/safety impact. */
    @Column(name = "impact_assessment", columnDefinition = "TEXT")
    private String impactAssessment;

    /** Whether a CAPA was raised as a result of this deviation. */
    @Column(name = "capa_required")
    @Builder.Default
    private Boolean capaRequired = false;

    /** Reference to the linked CAPA record number. */
    @Column(name = "capa_reference", length = 30)
    private String capaReference;

    /**
     * CAPA record number generated at HOD Assessment when capaRequired is
     * flipped on. The HOD either creates a fresh CAPA (which stamps its
     * number back here) or links to an existing CAPA #.
     *
     * Kept distinct from {@link #capaReference} so the audit diff makes
     * the lifecycle moment explicit (HOD assessment vs. earlier note).
     */
    @Column(name = "linked_capa_number", length = 30)
    private String linkedCapaNumber;

    /** Regulatory reporting required? */
    @Column(name = "regulatory_reportable")
    @Builder.Default
    private Boolean regulatoryReportable = false;

    /**
     * Set at the 2nd PENDING_QA_REVIEW pass. When true the workflow routes
     * through PENDING_SITE_HEAD before reaching Head QA.
     */
    @Column(name = "site_head_required")
    @Builder.Default
    private Boolean siteHeadRequired = false;

    /**
     * Set at the 2nd PENDING_QA_REVIEW pass. When true the workflow routes
     * through PENDING_CUSTOMER_COMMENT in parallel with RA evaluation.
     */
    @Column(name = "customer_comment_required")
    @Builder.Default
    private Boolean customerCommentRequired = false;

    /**
     * Closure cover-sheet narrative captured at PENDING_VERIFICATION by
     * the originating dept HOD — the final summary printed for closure.
     */
    @Column(name = "investigation_summary", columnDefinition = "TEXT")
    private String investigationSummary;

    @PrePersist
    private void prePersist() { setRecordType(QmsRecordType.DEVIATION); }
}
