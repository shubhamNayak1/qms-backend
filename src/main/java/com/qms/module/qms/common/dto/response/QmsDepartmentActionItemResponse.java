package com.qms.module.qms.common.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class QmsDepartmentActionItemResponse {
    private Long          id;
    private Long          deptCommentId;
    private String        description;
    private LocalDate     targetDate;
    private String        status;
    private LocalDateTime completedAt;
    private Long          completedById;
    private String        completedByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    // Batch C RED-5 — dept-declared extension used when the action item
    // is overdue and the dept still needs to upload evidence.
    private LocalDate     extensionDate;
    private String        extensionReason;
}
