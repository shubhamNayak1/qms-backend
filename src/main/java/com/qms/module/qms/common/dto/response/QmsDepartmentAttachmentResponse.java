package com.qms.module.qms.common.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class QmsDepartmentAttachmentResponse {
    private Long      id;
    private Long      departmentId;
    private String    departmentName;

    private String    attachmentRef;          // raw value (DMS doc id, free text, etc.)
    private String    attachmentNote;
    private String    status;                 // PENDING / APPROVED / REJECTED

    /**
     * Denormalised DMS lookup — populated on the response when
     * attachmentRef parses as a DMS document id and the document is
     * accessible. Lets the UI render "Doc-1234 v2.1 · Stability Protocol"
     * without making a follow-up call.
     */
    private Long      dmsDocumentId;
    private String    dmsDocumentNumber;
    private String    dmsDocumentTitle;
    private String    dmsDocumentVersion;

    private Long      decidedById;
    private String    decidedByName;
    private LocalDateTime decidedAt;
    private String    decisionNote;

    private LocalDateTime createdAt;
    private String    createdBy;
}
