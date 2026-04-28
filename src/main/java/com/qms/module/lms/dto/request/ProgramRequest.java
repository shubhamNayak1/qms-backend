package com.qms.module.lms.dto.request;

import com.qms.module.lms.enums.TrainingSubType;
import com.qms.module.lms.enums.TrainingType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
@Schema(description = "Request body for creating or updating a Training Program")
public class ProgramRequest {

    @NotBlank(message = "Code is required")
    @Size(max = 30, message = "Code must not exceed 30 characters")
    @Schema(example = "GMP-001")
    private String code;

    @NotBlank(message = "Title is required")
    @Size(max = 255)
    @Schema(example = "GMP Awareness Training — Level 1")
    private String title;

    private String description;

    // ── Training type ────────────────────────────────────────

    @NotNull(message = "Training type is required")
    @Schema(description = "Top-level type: SCHEDULED, SELF, or INDUCTION")
    private TrainingType trainingType;

    @Schema(description = "Sub-type: TEMPORARY, REGULAR, REFRESHER, OJT")
    private TrainingSubType trainingSubType;

    // ── Classification ───────────────────────────────────────

    @Schema(example = "GMP")
    private String category;

    @Schema(example = "Quality Assurance",
            description = "Single department. For INDUCTION use 'departments' (comma-separated).")
    private String department;

    @Schema(description = "Comma-separated departments — mainly for INDUCTION type",
            example = "QA,Production,Warehouse")
    private String departments;

    @Schema(example = "GMP,mandatory,awareness,all-staff")
    private String tags;

    private Boolean isMandatory;

    // ── Trainer / coordinator / location ─────────────────────

    @Schema(description = "Internal user ID of the trainer (null for external/vendor trainer)")
    private Long trainerId;

    @Schema(example = "Dr. John Smith")
    private String trainerName;

    @Schema(description = "External vendor / training company name (when trainer is external)")
    private String vendorName;

    @Schema(description = "Internal user ID of the coordinator (defaults to creator)")
    private Long coordinatorId;

    @Schema(example = "Jane Doe")
    private String coordinatorName;

    @Schema(example = "Training Room B, Block 3")
    private String location;

    @Schema(example = "https://meet.google.com/abc-xyz")
    private String conferenceLink;

    // ── Exam ─────────────────────────────────────────────────

    @Schema(description = "Whether an exam/assessment is required (toggle ON/OFF)")
    private Boolean examEnabled;

    @Schema(description = "Deprecated — use examEnabled instead. Kept for backward compatibility.")
    private Boolean assessmentRequired;

    // ── Duration / certificate / deadlines ───────────────────

    @Min(1) @Max(9999)
    private Integer estimatedDurationMinutes;

    @Min(1) @Max(10)
    private Integer certificateValidityYears;

    @Min(1) @Max(365)
    private Integer completionDeadlineDays;

    @Min(0) @Max(100)
    private Integer passScore;

    @Min(1) @Max(10)
    private Integer maxAttempts;

    private Long ownerId;
}
