package com.qms.module.lms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Schema(description = "Attendance submission for one or more trainees in a session")
public class AttendanceRequest {

    @Schema(description = "List of individual trainee attendance records")
    @NotNull
    private List<AttendeeRecord> attendees;

    @Data
    public static class AttendeeRecord {

        @NotNull(message = "User ID is required")
        private Long userId;

        private String userName;

        @NotNull(message = "Enrollment ID is required")
        private Long enrollmentId;

        @NotNull(message = "Present flag is required")
        @Schema(description = "true = present, false = absent")
        private Boolean isPresent;

        @Schema(description = "Actual date of attendance (defaults to session date if null)")
        private LocalDate attendanceDate;

        private String notes;
    }
}
