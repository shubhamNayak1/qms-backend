package com.qms.module.qms.incident.entity;

import com.qms.common.enums.QmsRecordType;
import com.qms.module.qms.common.entity.QmsRecord;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "qms_incident", indexes = {
        @Index(name = "idx_inc_status",   columnList = "status"),
        @Index(name = "idx_inc_priority", columnList = "priority"),
        @Index(name = "idx_inc_number",   columnList = "record_number", unique = true),
        @Index(name = "idx_inc_severity", columnList = "severity")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Incident extends QmsRecord {

    /** Safety / Quality / Environmental / Equipment / Personnel */
    @Column(name = "incident_type", length = 80)
    private String incidentType;

    /** Minor / Major / Critical */
    @Column(name = "severity", length = 30)
    private String severity;

    @Column(name = "location", length = 150)
    private String location;

    @Column(name = "occurrence_date")
    private LocalDate occurrenceDate;

    /** Person who initially reported the incident. */
    @Column(name = "reported_by", length = 150)
    private String reportedBy;

    /** Immediate containment actions taken at the time of the incident. */
    @Column(name = "immediate_action", columnDefinition = "TEXT")
    private String immediateAction;

    @Column(name = "investigation_details", columnDefinition = "TEXT")
    private String investigationDetails;

    @Column(name = "capa_reference", length = 30)
    private String capaReference;

    /**
     * Set by HOD at PENDING_HOD when the Incident needs a CAPA cross-link.
     * Drives the conditional CAPA/Add branch on both Lab and General paths.
     */
    @Column(name = "capa_required")
    private Boolean capaRequired = false;

    /**
     * CAPA record number generated at HOD Assessment when capaRequired flips
     * on. Distinct from {@link #capaReference} so the audit diff captures
     * the lifecycle moment unambiguously.
     */
    @Column(name = "linked_capa_number", length = 30)
    private String linkedCapaNumber;

    /** Were any personnel injured? */
    @Column(name = "injury_involved")
    private Boolean injuryInvolved = false;

    @Column(name = "injury_details", columnDefinition = "TEXT")
    private String injuryDetails;

    /**
     * Incident sub-type for routing.
     * LABORATORY — OOS/OOT laboratory investigation flow
     * GENERAL — general safety/quality incident flow
     */
    @Column(name = "incident_sub_type", length = 20)
    private String incidentSubType;  // LABORATORY | GENERAL

    /** Whether lab retesting / additional analysis is required (Lab branch fork). */
    @Column(name = "retesting_required")
    private Boolean retestingRequired = false;

    /** Whether a Deviation record needs to be raised as a result of this incident. */
    @Column(name = "deviation_required")
    private Boolean deviationRequired = false;

    /**
     * Set by the QA Reviewer at Assessment by QA. Drives the optional
     * PENDING_SITE_HEAD branch — when false, the workflow skips the Site
     * Head step and goes straight to PENDING_HEAD_QA.
     */
    @Column(name = "site_head_required")
    private Boolean siteHeadRequired = false;

    /**
     * Lab + No-Retest path only — the "Abnormality in Proposed RA"
     * narrative captured by the QA Reviewer at Assessment by QA. Records
     * how the lab proposes to handle the abnormality without retesting.
     */
    @Column(name = "abnormality_remedial_action", columnDefinition = "TEXT")
    private String abnormalityRemedialAction;

    /**
     * Cross-link to the Deviation that was spawned from this Incident.
     * Populated when {@link #deviationRequired} is true and QA confirms;
     * also stamped onto {@link com.qms.module.qms.deviation.entity.Deviation#parentIncidentId}.
     * The Incident's status is moved to {@code DEVIATION_SPAWNED} once the
     * cross-link is created.
     */
    @Column(name = "spawned_deviation_id")
    private Long spawnedDeviationId;

    /**
     * Denormalised Deviation record number for the cross-link UI — avoids
     * a join when rendering the Incident detail page's "Spawned Deviation"
     * banner.
     */
    @Column(name = "spawned_deviation_number", length = 30)
    private String spawnedDeviationNumber;

    /**
     * Closure verification narrative captured at PENDING_VERIFICATION by
     * the originating dept HOD.
     */
    @Column(name = "verification_narrative", columnDefinition = "TEXT")
    private String verificationNarrative;

    @PrePersist
    private void prePersist() { setRecordType(QmsRecordType.INCIDENT); }
}
