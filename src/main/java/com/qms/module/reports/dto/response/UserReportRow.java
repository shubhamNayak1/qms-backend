package com.qms.module.reports.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data @Builder @JsonInclude(JsonInclude.Include.NON_NULL)
public class UserReportRow {

    private Long          id;
    private String        username;
    private String        fullName;
    private String        email;
    private String        department;
    private List<String>  roles;
    private Boolean       active;
    private Boolean       locked;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;

    // Training compliance summary (joined from LMS if available)
    private long          totalTrainingsAssigned;
    private long          trainingsCompleted;
    private long          trainingsOverdue;
    private double        complianceRate;
}
