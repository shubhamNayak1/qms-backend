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
    private Boolean capaRequired = false;

    /** Reference to the linked CAPA record number. */
    @Column(name = "capa_reference", length = 30)
    private String capaReference;

    /** Regulatory reporting required? */
    @Column(name = "regulatory_reportable")
    private Boolean regulatoryReportable = false;

    @PrePersist
    private void prePersist() { setRecordType(QmsRecordType.DEVIATION); }
}
