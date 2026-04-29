package com.qms.module.qmsaudit.dto.request;

import com.qms.module.qmsaudit.enums.AuditType;
import lombok.Data;

import java.time.LocalDate;

/** All fields optional — only non-null values are applied. */
@Data
public class UpdateAuditScheduleRequest {

    private String    title;
    private AuditType auditType;
    private String    scope;
    private Long      leadAuditorId;
    private String    leadAuditorName;
    private LocalDate scheduledDate;
    private LocalDate completedDate;
    private String    findings;
    private String    observations;
}
