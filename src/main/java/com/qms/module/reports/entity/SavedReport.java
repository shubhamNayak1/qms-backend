package com.qms.module.reports.entity;

import com.qms.module.reports.enums.ExportFormat;
import com.qms.module.reports.enums.ReportModule;
import com.qms.module.reports.enums.ReportStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "saved_reports")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class SavedReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportModule module;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ExportFormat format = ExportFormat.EXCEL;

    private LocalDate dateFrom;
    private LocalDate dateTo;

    /** JSON array of dimension field keys e.g. ["status","department"] */
    @Column(columnDefinition = "TEXT")
    private String dimensions;

    /** JSON array of metric field keys e.g. ["record_number","title"] */
    @Column(columnDefinition = "TEXT")
    private String metrics;

    /** JSON object with extra filter key/values */
    @Column(name = "extra_filters", columnDefinition = "TEXT")
    private String extraFilters;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ReportStatus status = ReportStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String filePath;

    private String fileName;
    private Long fileSizeBytes;

    private LocalDateTime lastRunAt;

    @Column(nullable = false)
    @Builder.Default
    private Integer runCount = 0;

    @Column(columnDefinition = "TEXT")
    private String lastRunError;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isDisabled = false;

    private Long createdByUserId;
    private String createdByUsername;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
