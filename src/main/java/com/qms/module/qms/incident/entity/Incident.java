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

    /** Were any personnel injured? */
    @Column(name = "injury_involved")
    private Boolean injuryInvolved = false;

    @Column(name = "injury_details", columnDefinition = "TEXT")
    private String injuryDetails;

    @PrePersist
    private void prePersist() { setRecordType(QmsRecordType.INCIDENT); }
}
