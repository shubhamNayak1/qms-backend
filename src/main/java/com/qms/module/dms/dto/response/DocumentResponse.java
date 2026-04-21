package com.qms.module.dms.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.qms.module.dms.enums.AccessLevel;
import com.qms.module.dms.enums.DocumentCategory;
import com.qms.module.dms.enums.DocumentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocumentResponse {

    private Long            id;
    private String          docNumber;
    private String          version;
    private String          fullVersion;
    private Integer         majorVersion;
    private Integer         minorVersion;
    private String          title;
    private String          description;
    private DocumentCategory category;
    private DocumentStatus  status;
    private AccessLevel     accessLevel;
    private String          department;
    private String          tags;

    // Ownership
    private Long   ownerId;
    private String ownerName;
    private Long   authorId;
    private String authorName;

    // File info (safe subset — never expose storageKey)
    private String originalFilename;
    private String mimeType;
    private Long   fileSizeBytes;
    private String fileSizeHuman;          // e.g. "2.3 MB"
    private String sha256Checksum;

    // Dates
    private LocalDate     effectiveDate;
    private LocalDate     expiryDate;
    private LocalDate     reviewDate;
    private LocalDateTime publishedAt;
    private LocalDateTime obsoletedAt;

    // Approval
    private Long          approvedById;
    private String        approvedByName;
    private LocalDateTime approvedAt;
    private String        approvalComments;
    private String        rejectionReason;

    // Versioning
    private Long   parentId;
    private String changeSummary;
    private Boolean isControlled;
    private Long    downloadCount;

    // Computed flags
    private boolean expired;
    private boolean expiringSoon;
    private boolean dueForReview;

    // Workflow
    private Set<DocumentStatus> allowedTransitions;
    private List<ApprovalResponse> approvals;

    // Audit
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String        createdBy;
    private String        updatedBy;

    // Download URL (populated on download request, not on list/get)
    private String downloadUrl;     // signed URL for cloud, or null for local (use /download endpoint)
}
