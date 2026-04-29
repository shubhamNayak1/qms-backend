package com.qms.module.reports.entity;

import com.qms.module.reports.enums.RunStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "report_run_history")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ReportRunHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    private Long triggeredByUserId;
    private String triggeredByUsername;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RunStatus status;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime completedAt;
    private Long durationMs;
    private Long rowCount;

    @Column(columnDefinition = "TEXT")
    private String filePath;

    private Long fileSizeBytes;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @CreatedDate
    private LocalDateTime createdAt;
}
