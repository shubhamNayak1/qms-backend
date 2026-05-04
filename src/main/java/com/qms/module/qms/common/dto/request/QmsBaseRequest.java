package com.qms.module.qms.common.dto.request;

import com.qms.common.enums.Priority;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Base class for all QMS sub-module create/update request DTOs.
 * Each sub-module extends this and adds its specific fields.
 *
 * Note: this is NOT abstract because Jackson needs to instantiate it
 * for sub-module DTOs in tests. Sub-module request classes extend it.
 */
@Getter
@Setter
public class QmsBaseRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    @Schema(example = "Critical Sterility Failure in Batch 2024-001")
    private String title;

    @Schema(example = "During routine inspection, sterility testing failed for batch 2024-001.")
    private String description;

    @NotNull(message = "Priority is required")
    @Schema(example = "HIGH")
    private Priority priority;

    @Schema(description = "Database ID of the user to assign this record to", example = "42")
    private Long assignedToId;

    @Schema(example = "Manufacturing")
    private String department;

    @Schema(description = "FK to departments.id — drives positional workflow checks", example = "5")
    private Long departmentId;

    @FutureOrPresent(message = "Due date must be today or in the future")
    @Schema(description = "ISO date e.g. 2024-12-31")
    private LocalDate dueDate;

    @FutureOrPresent
    private LocalDate targetCompletionDate;

    private String rootCause;
    private String correctiveAction;

    @Size(max = 2000)
    private String comments;

    // ── Shared fields lifted to QmsRecord (V19) ──────────────
    // These let stage-specific panels (HOD review, RA review, verification)
    // update the same record via PUT without needing per-module endpoints.

    /** Risk assessment narrative, captured during HOD / QA review. */
    private String riskAssessment;

    /** Critical / Major / Minor — set during RA evaluation. */
    @Size(max = 20)
    private String category;

    private Boolean customerCommunicationRequired;

    @Size(max = 150)
    private String customerRepresentative;

    private String customerComment;

    // Verification phase
    private String   verificationActionTaken;
    private LocalDate verificationEffectiveOn;
    private Boolean  verificationDocumentsReissue;
    private String   verificationOtherComments;
    private String   verificationRegCommunication;
}
