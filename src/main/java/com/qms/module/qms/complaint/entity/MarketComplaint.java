package com.qms.module.qms.complaint.entity;

import com.qms.common.enums.QmsRecordType;
import com.qms.module.qms.common.entity.QmsRecord;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "qms_market_complaint", indexes = {
        @Index(name = "idx_mc_status",   columnList = "status"),
        @Index(name = "idx_mc_priority", columnList = "priority"),
        @Index(name = "idx_mc_number",   columnList = "record_number", unique = true),
        @Index(name = "idx_mc_category", columnList = "complaint_category"),
        @Index(name = "idx_mc_product",  columnList = "product_name")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MarketComplaint extends QmsRecord {

    // ── Origin / linkage ──────────────────────────────────────
    /**
     * NEW or EXISTING.
     *  • NEW       — fresh complaint, no parent.
     *  • EXISTING  — follow-up complaint that references {@link #parentComplaintId}.
     */
    @Column(name = "complaint_origin", length = 20)
    private String complaintOrigin;

    /** Parent market-complaint id — populated when complaintOrigin = EXISTING. */
    @Column(name = "parent_complaint_id")
    private Long parentComplaintId;

    /**
     * What the complaint is about — Product / Packing / Transportation /
     * Labels / Drum / Shipper / Carton / Bag.
     */
    @Column(name = "complaint_subject", length = 50)
    private String complaintSubject;

    // ── Customer info ─────────────────────────────────────────
    @Column(name = "customer_name", length = 150)
    private String customerName;

    @Column(name = "customer_contact", length = 200)
    private String customerContact;

    @Column(name = "customer_country", length = 80)
    private String customerCountry;

    // ── Product info ──────────────────────────────────────────
    @Column(name = "product_name", length = 150)
    private String productName;

    @Column(name = "batch_number", length = 80)
    private String batchNumber;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    // ── Complaint classification ───────────────────────────────
    /** Quality / Safety / Packaging / Labeling / Delivery / Service */
    @Column(name = "complaint_category", length = 80)
    private String complaintCategory;

    /** Phone / Email / Portal / Field Visit / Letter */
    @Column(name = "complaint_source", length = 80)
    private String complaintSource;

    @Column(name = "received_date")
    private LocalDate receivedDate;

    // ── Regulatory ────────────────────────────────────────────
    /** Must this complaint be reported to a health authority? */
    @Column(name = "reportable_to_authority")
    private Boolean reportableToAuthority = false;

    @Column(name = "authority_report_reference", length = 100)
    private String authorityReportReference;

    @Column(name = "authority_report_date")
    private LocalDate authorityReportDate;

    // ── Resolution ────────────────────────────────────────────
    @Column(name = "resolution_details", columnDefinition = "TEXT")
    private String resolutionDetails;

    @Column(name = "customer_response", columnDefinition = "TEXT")
    private String customerResponse;

    @Column(name = "customer_notified_date")
    private LocalDate customerNotifiedDate;

    @Column(name = "customer_satisfied")
    private Boolean customerSatisfied;

    @Column(name = "capa_reference", length = 30)
    private String capaReference;

    /** Set by QA Reviewer at PENDING_INVESTIGATION. */
    @Column(name = "capa_required")
    private Boolean capaRequired = false;

    /** Was the complained product returned for investigation? */
    @Column(name = "sample_returned")
    private Boolean sampleReturned = false;

    // ── QA Investigation fields ───────────────────────────────
    @Column(name = "investigation_findings", columnDefinition = "TEXT")
    private String investigationFindings;

    @Column(name = "impact_assessment", columnDefinition = "TEXT")
    private String impactAssessment;

    @PrePersist
    private void prePersist() { setRecordType(QmsRecordType.MARKET_COMPLAINT); }
}
