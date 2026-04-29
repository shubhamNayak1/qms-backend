package com.qms.module.qmsaudit.dto.response;

import com.qms.module.qmsaudit.enums.AuditScheduleStatus;
import com.qms.module.qmsaudit.enums.AuditType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class AuditScheduleResponse {

    private Long                 id;
    private String               auditNumber;        // AUD-202504-0001
    private String               title;
    private AuditType            auditType;
    private String               scope;

    private Long                 leadAuditorId;
    private String               leadAuditorName;

    private LocalDate            scheduledDate;
    private LocalDate            completedDate;

    private String               findings;
    private String               observations;

    private AuditScheduleStatus  status;
    private boolean              overdue;            // scheduled past & still PLANNED

    private String               createdBy;
    private LocalDateTime        createdAt;
    private LocalDateTime        updatedAt;
}
