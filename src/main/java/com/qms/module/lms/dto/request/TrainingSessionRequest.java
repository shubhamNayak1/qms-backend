package com.qms.module.lms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Schema(description = "Request to create or update a training session")
public class TrainingSessionRequest {

    @NotNull(message = "Session date is required")
    @Schema(example = "2025-06-15")
    private LocalDate sessionDate;

    @Schema(description = "For multi-day sessions. Leave null for single-day.", example = "2025-06-16")
    private LocalDate sessionEndDate;

    @Schema(example = "09:00")
    private LocalTime startTime;

    @Schema(example = "17:00")
    private LocalTime endTime;

    @Schema(example = "Training Room 2, Block A")
    private String venue;

    @Schema(example = "https://meet.google.com/abc-xyz")
    private String meetingLink;

    @Schema(description = "Override trainer for this session (defaults to program trainer)")
    private Long trainerId;
    private String trainerName;

    @Schema(description = "Override coordinator for this session")
    private Long coordinatorId;
    private String coordinatorName;

    @Schema(example = "30")
    private Integer maxParticipants;

    private String notes;
}
