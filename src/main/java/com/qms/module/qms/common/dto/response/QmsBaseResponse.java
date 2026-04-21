package com.qms.module.qms.common.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.qms.common.enums.Priority;
import com.qms.common.enums.QmsRecordType;
import com.qms.common.enums.QmsStatus;
import com.qms.module.qms.common.workflow.StatusHistoryEntry;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Base response DTO — contains all fields from QmsRecord.
 * Sub-module response classes extend this and add their specific fields.
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QmsBaseResponse {

    private Long            id;
    private String          recordNumber;
    private QmsRecordType   recordType;
    private String          title;
    private String          description;
    private QmsStatus       status;
    private Priority        priority;

    // Assignment
    private Long   assignedToId;
    private String assignedToName;
    private Long   raisedById;
    private String raisedByName;
    private String department;

    // Dates
    private LocalDate dueDate;
    private LocalDate closedDate;
    private LocalDate targetCompletionDate;

    // Approval
    private Long          approvedById;
    private String        approvedByName;
    private LocalDateTime approvedAt;
    private String        approvalComments;

    // Analysis
    private String rootCause;
    private String correctiveAction;
    private String comments;

    // Computed
    private boolean overdue;
    private Set<QmsStatus> allowedTransitions;

    // Audit trail
    private List<StatusHistoryEntry> statusHistory;

    // BaseEntity fields
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String        createdBy;
    private String        updatedBy;
}
