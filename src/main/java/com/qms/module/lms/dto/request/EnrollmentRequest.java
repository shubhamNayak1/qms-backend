package com.qms.module.lms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "Assign a training program to a single user")
public class EnrollmentRequest {

    @NotNull(message = "userId is required")
    @Schema(example = "42")
    private Long userId;

    @NotNull(message = "programId is required")
    @Schema(example = "7")
    private Long programId;

    @Schema(description = "Override the program's default deadline days")
    private LocalDate dueDate;

    @Schema(example = "Required for annual GMP refresher compliance")
    private String assignmentReason;
}
