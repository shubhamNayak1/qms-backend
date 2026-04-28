package com.qms.module.lms.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.qms.module.lms.enums.SessionStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TrainingSessionResponse {

    private Long          id;
    private Long          programId;
    private String        programCode;
    private String        programTitle;

    private LocalDate     sessionDate;
    private LocalDate     sessionEndDate;
    private LocalTime     startTime;
    private LocalTime     endTime;

    private String        venue;
    private String        meetingLink;

    private Long          trainerId;
    private String        trainerName;
    private Long          coordinatorId;
    private String        coordinatorName;

    private Integer       maxParticipants;
    private SessionStatus status;
    private String        cancellationReason;
    private String        notes;

    private boolean       withinAttendanceWindow;
    private long          presentCount;
    private long          totalEnrolled;

    private List<AttendanceSummary> attendance;

    private LocalDateTime createdAt;
    private String        createdBy;

    @Data @Builder
    public static class AttendanceSummary {
        private Long      id;
        private Long      userId;
        private String    userName;
        private Long      enrollmentId;
        private Boolean   isPresent;
        private LocalDate attendanceDate;
        private String    markedBy;
        private String    notes;
    }
}
