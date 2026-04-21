package com.qms.module.audit.dto.request;

import com.qms.common.enums.AuditAction;
import com.qms.common.enums.AuditModule;
import com.qms.common.enums.AuditOutcome;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
@Schema(description = "Search/filter criteria for audit logs. All fields are optional.")
public class AuditSearchRequest {

    @Schema(description = "Filter by acting user's database ID", example = "42")
    private Long userId;

    @Schema(description = "Partial username match (case-insensitive)", example = "john")
    private String username;

    @Schema(description = "Filter by action type", example = "CREATE")
    private AuditAction action;

    @Schema(description = "Filter by QMS module", example = "CAPA")
    private AuditModule module;

    @Schema(description = "Entity class name, e.g. Capa, Document, User", example = "Capa")
    private String entityType;

    @Schema(description = "Primary key of the specific entity", example = "17")
    private Long entityId;

    @Schema(description = "Filter by operation outcome", example = "FAILURE")
    private AuditOutcome outcome;

    @Schema(description = "Filter by client IP address", example = "192.168.1.100")
    private String ipAddress;

    @Schema(description = "Range start (inclusive) — ISO-8601", example = "2024-01-01T00:00:00")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime from;

    @Schema(description = "Range end (inclusive) — ISO-8601", example = "2024-12-31T23:59:59")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime to;

    @Min(0)
    @Schema(description = "Zero-based page number", defaultValue = "0")
    private int page = 0;

    @Min(1) @Max(200)
    @Schema(description = "Page size — max 200", defaultValue = "50")
    private int size = 50;
}
