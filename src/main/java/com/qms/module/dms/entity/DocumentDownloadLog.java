package com.qms.module.dms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Append-only download audit log.
 * Required for GxP compliance — every access to a controlled document
 * must be traceable. Never update or delete rows in this table.
 */
@Entity
@Table(
    name = "dms_download_logs",
    indexes = {
        @Index(name = "idx_dl_document",  columnList = "document_id"),
        @Index(name = "idx_dl_user",      columnList = "user_id"),
        @Index(name = "idx_dl_timestamp", columnList = "downloaded_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentDownloadLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false, updatable = false)
    private Long documentId;

    @Column(name = "doc_number", nullable = false, length = 40, updatable = false)
    private String docNumber;

    @Column(name = "version", nullable = false, length = 10, updatable = false)
    private String version;

    @Column(name = "user_id", updatable = false)
    private Long userId;

    @Column(name = "username", length = 100, updatable = false)
    private String username;

    @Column(name = "ip_address", length = 60, updatable = false)
    private String ipAddress;

    @Column(name = "user_agent", length = 500, updatable = false)
    private String userAgent;

    @Column(name = "downloaded_at", nullable = false, updatable = false)
    private LocalDateTime downloadedAt;

    /** Whether the user acknowledged reading the document (controlled distribution). */
    @Column(name = "acknowledged", nullable = false)
    @Builder.Default
    private Boolean acknowledged = false;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @PrePersist
    private void onCreate() {
        downloadedAt = LocalDateTime.now();
    }
}
