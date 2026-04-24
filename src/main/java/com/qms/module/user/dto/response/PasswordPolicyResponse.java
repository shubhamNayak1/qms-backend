package com.qms.module.user.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class PasswordPolicyResponse {

    private Long      id;
    private int       passwordLengthMin;
    private int       passwordLengthMax;
    private int       alphaMin;
    private int       numericMin;
    private int       specialCharMin;
    private int       upperCaseMin;
    private int       numberOfLoginAttempts;
    private int       validPeriod;
    private int       previousPasswordAttemptTrack;
    private LocalDate effectiveDate;

    /** True when this policy's effectiveDate is today or in the past */
    private boolean   isActive;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String        createdBy;
}
