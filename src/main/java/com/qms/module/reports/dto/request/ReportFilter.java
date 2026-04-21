package com.qms.module.reports.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

/**
 * Universal filter DTO used by all report endpoints.
 *
 * All fields are optional. When omitted, that dimension is not filtered.
 * Combining multiple fields produces an AND query.
 *
 * Example:
 *   status=OPEN&priority=HIGH&dateFrom=2024-01-01&dateTo=2024-03-31
 *   → returns all OPEN HIGH-priority records in Q1 2024
 */
@Data
@Schema(description = "Universal filter for all report types — all fields optional")
public class ReportFilter {

    // ── Date range ────────────────────────────────────────────
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Schema(description = "Start of date range (createdAt >= from)", example = "2024-01-01")
    private LocalDate dateFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Schema(description = "End of date range (createdAt <= to)", example = "2024-12-31")
    private LocalDate dateTo;

    // ── Status / Priority ─────────────────────────────────────
    @Schema(description = "Filter by status(es)", example = "[\"OPEN\",\"IN_PROGRESS\"]")
    private List<String> statuses;

    @Schema(description = "Filter by priority(ies)", example = "[\"HIGH\",\"CRITICAL\"]")
    private List<String> priorities;

    // ── Assignment / Ownership ────────────────────────────────
    @Schema(description = "Filter by assigned user ID", example = "42")
    private Long assignedToId;

    @Schema(description = "Filter by department", example = "Manufacturing")
    private String department;

    @Schema(description = "Filter by raised-by user ID")
    private Long raisedById;

    // ── Record-type-specific filters ──────────────────────────

    // CAPA
    @Schema(description = "[CAPA] Filter by CAPA source", example = "Audit")
    private String source;

    @Schema(description = "[CAPA] Filter by CAPA type", example = "Corrective")
    private String capaType;

    // Deviation
    @Schema(description = "[Deviation] Filter by type", example = "Unplanned")
    private String deviationType;

    @Schema(description = "[Deviation] Filter by process area", example = "Filling Line 3")
    private String processArea;

    @Schema(description = "[Deviation] Filter regulatory-reportable only", example = "true")
    private Boolean regulatoryReportable;

    // Incident
    @Schema(description = "[Incident] Filter by severity", example = "Critical")
    private String severity;

    @Schema(description = "[Incident] Filter by incident type", example = "Safety")
    private String incidentType;

    @Schema(description = "[Incident] Filter injury-involved only", example = "true")
    private Boolean injuryInvolved;

    // Change Control
    @Schema(description = "[ChangeControl] Filter by change type", example = "Equipment")
    private String changeType;

    @Schema(description = "[ChangeControl] Filter by risk level", example = "High")
    private String riskLevel;

    // Market Complaint
    @Schema(description = "[Complaint] Filter by category", example = "Quality")
    private String complaintCategory;

    @Schema(description = "[Complaint] Filter reportable-to-authority only")
    private Boolean reportableToAuthority;

    @Schema(description = "[Complaint] Filter by customer country", example = "Germany")
    private String customerCountry;

    // User report
    @Schema(description = "[User] Filter by role name", example = "QA_OFFICER")
    private String role;

    @Schema(description = "[User] Filter active/inactive users only")
    private Boolean active;

    // ── Overdue filter ────────────────────────────────────────
    @Schema(description = "Return only overdue records (dueDate < today)", example = "false")
    private Boolean overdueOnly;

    // ── Text search ──────────────────────────────────────────
    @Schema(description = "Full-text search on title/description/record number")
    private String search;

    // ── Pagination ────────────────────────────────────────────
    @Min(0)  private int page = 0;
    @Min(1) @Max(500) private int size = 50;

    // ── Sort ─────────────────────────────────────────────────
    @Schema(description = "Sort field", example = "createdAt")
    private String sortBy = "createdAt";

    @Schema(description = "Sort direction: ASC or DESC", example = "DESC")
    private String sortDir = "DESC";
}
