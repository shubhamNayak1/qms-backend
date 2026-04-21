package com.qms.module.audit.dto.request;

import com.qms.common.enums.AuditAction;
import com.qms.common.enums.AuditModule;
import com.qms.common.enums.AuditOutcome;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Request body for manually submitting an audit log entry")
public class ManualAuditRequest {

    @NotNull(message = "action is required")
    private AuditAction action;

    @NotNull(message = "module is required")
    private AuditModule module;

    @Schema(description = "Entity class name", example = "ExternalSystem")
    private String entityType;

    @Schema(description = "Entity primary key", example = "99")
    private Long entityId;

    @Schema(description = "Human-readable description of what happened")
    private String description;

    @Schema(description = "JSON string of the entity state before the operation")
    private String oldValue;

    @Schema(description = "JSON string of the entity state after the operation")
    private String newValue;

    private AuditOutcome outcome;

    @Schema(description = "Correlation ID for distributed tracing")
    private String correlationId;
}
