package com.qms.module.qms.common.entity;

import com.qms.common.enums.QmsRecordType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Generic local-file attachment for a QMS record (Round-2 A1).
 *
 * Sits in qms_record_attachments and is identified on the parent record by
 * an initial_attachment_ref value of "QMS-ATT-{id}". The Change Control
 * response resolver checks the prefix and joins either DMS document or this
 * table accordingly.
 *
 * The file bytes are stored inline (BYTEA). For files larger than 10 MB the
 * upload endpoint rejects the request so the DB doesn't bloat — for those
 * cases the user should check the file into DMS first and reference its id.
 */
@Entity
@Table(name = "qms_record_attachments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QmsRecordAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "record_type", nullable = false, length = 40)
    private QmsRecordType recordType;

    @Column(name = "record_id", nullable = false)
    private Long recordId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", length = 120)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "data", nullable = false)
    private byte[] data;

    @Column(name = "uploaded_by_id")
    private Long uploadedById;

    @Column(name = "uploaded_by_name", length = 160)
    private String uploadedByName;

    @Column(name = "uploaded_at", nullable = false)
    @Builder.Default
    private LocalDateTime uploadedAt = LocalDateTime.now();

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = Boolean.FALSE;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by_id")
    private Long deletedById;
}
