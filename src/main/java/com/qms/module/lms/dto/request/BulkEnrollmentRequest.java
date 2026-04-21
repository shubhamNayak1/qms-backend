package com.qms.module.lms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Schema(description = "Assign a training program to multiple users at once")
public class BulkEnrollmentRequest {

    @NotNull(message = "programId is required")
    private Long programId;

    @NotEmpty(message = "At least one userId is required")
    @Schema(example = "[42, 43, 44, 45]")
    private List<Long> userIds;

    @Schema(description = "Shared deadline for all users in this bulk assignment")
    private LocalDate dueDate;

    @Schema(example = "Annual GMP refresher — all QA staff")
    private String assignmentReason;
}
