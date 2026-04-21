package com.qms.module.audit.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.qms.common.enums.AuditAction;
import com.qms.common.enums.AuditModule;
import com.qms.common.enums.AuditOutcome;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuditLogResponse {

    private Long        id;

    // ── Who ──────────────────────────────────────────────────
    private Long        userId;
    private String      username;
    private String      userFullName;
    private String      userDepartment;

    // ── What ─────────────────────────────────────────────────
    private AuditAction action;
    private AuditModule module;
    private String      entityType;
    private Long        entityId;
    private String      description;

    // ── Before / After ───────────────────────────────────────
    private String      oldValue;
    private String      newValue;

    // ── Outcome ──────────────────────────────────────────────
    private AuditOutcome outcome;
    private String       errorType;
    private String       errorMessage;

    // ── Network context ──────────────────────────────────────
    private String      ipAddress;
    private String      requestUri;
    private String      correlationId;
    private String      sessionId;

    // ── Timing ───────────────────────────────────────────────
    private LocalDateTime timestamp;
    private Long          durationMs;
}
