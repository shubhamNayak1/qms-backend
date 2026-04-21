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

    @FutureOrPresent(message = "Due date must be today or in the future")
    @Schema(description = "ISO date e.g. 2024-12-31")
    private LocalDate dueDate;

    @FutureOrPresent
    private LocalDate targetCompletionDate;

    private String rootCause;
    private String correctiveAction;

    @Size(max = 2000)
    private String comments;
}
