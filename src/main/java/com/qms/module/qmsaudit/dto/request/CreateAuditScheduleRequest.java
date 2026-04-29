package com.qms.module.qmsaudit.dto.request;

import com.qms.module.qmsaudit.enums.AuditType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateAuditScheduleRequest {

    @NotBlank(message = "Title is required")
    private String title;

    @NotNull(message = "Audit type is required (INTERNAL, EXTERNAL, SUPPLIER, REGULATORY)")
    private AuditType auditType;

    private String scope;

    /** Optional — if provided, name is resolved from the user record */
    private Long leadAuditorId;

    /** Plain-text name if auditor is not a system user */
    private String leadAuditorName;

    @NotNull(message = "Scheduled date is required")
    private LocalDate scheduledDate;

    private LocalDate completedDate;

    private String findings;
    private String observations;
}
