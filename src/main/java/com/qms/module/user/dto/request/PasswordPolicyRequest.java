package com.qms.module.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "Request body for creating or updating a password policy")
public class PasswordPolicyRequest {

    @Min(value = 1, message = "Minimum password length must be at least 1")
    @Schema(description = "Minimum password length", example = "8")
    private int passwordLengthMin;

    @Min(value = 1, message = "Maximum password length must be at least 1")
    @Schema(description = "Maximum password length", example = "128")
    private int passwordLengthMax;

    @Min(value = 0, message = "alphaMin cannot be negative")
    @Schema(description = "Minimum number of alphabetic characters", example = "1")
    private int alphaMin;

    @Min(value = 0, message = "numericMin cannot be negative")
    @Schema(description = "Minimum number of numeric characters", example = "1")
    private int numericMin;

    @Min(value = 0, message = "specialCharMin cannot be negative")
    @Schema(description = "Minimum number of special characters (!@#$... etc.)", example = "1")
    private int specialCharMin;

    @Min(value = 0, message = "upperCaseMin cannot be negative")
    @Schema(description = "Minimum number of uppercase letters", example = "1")
    private int upperCaseMin;

    @Min(value = 1, message = "numberOfLoginAttempts must be at least 1")
    @Schema(description = "Max failed login attempts before account lockout", example = "5")
    private int numberOfLoginAttempts;

    @Min(value = 0, message = "validPeriod cannot be negative")
    @Schema(description = "Password expiry in days. 0 means passwords never expire.", example = "90")
    private int validPeriod;

    @Min(value = 0, message = "previousPasswordAttemptTrack cannot be negative")
    @Schema(description = "Number of previous passwords the user cannot reuse. 0 disables history check.", example = "5")
    private int previousPasswordAttemptTrack;

    @NotNull(message = "effectiveDate is required")
    @Schema(description = "Date from which this policy becomes active (ISO-8601 date)", example = "2026-05-01")
    private LocalDate effectiveDate;
}
