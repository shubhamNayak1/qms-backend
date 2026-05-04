package com.qms.module.user.dto.request;

import com.qms.module.user.validator.ValidPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;
import java.util.Set;

@Data
@Schema(description = "Request body for creating a new user account")
public class CreateUserRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 80, message = "Username must be between 3 and 80 characters")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$",
             message = "Username may only contain letters, digits, dots, underscores, and hyphens")
    @Schema(example = "john.doe")
    private String username;

    @Email(message = "Must be a valid email address")
    @Size(max = 150, message = "Email must not exceed 150 characters")
    @Schema(example = "john.doe@company.com", description = "Optional — leave blank if user has no email")
    private String email;

    @NotBlank(message = "Password is required")
    @ValidPassword
    @Schema(example = "SecurePass@123",
            description = "Min 8 chars, at least one uppercase, one lowercase, one digit, one special char")
    private String password;

    @NotBlank(message = "First name is required")
    @Size(min = 1, max = 80, message = "First name must not exceed 80 characters")
    @Schema(example = "John")
    private String firstName;

    @Size(max = 80, message = "Last name must not exceed 80 characters")
    @Schema(example = "Doe", description = "Optional — surname is not mandatory")
    private String lastName;

    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "^\\+?[0-9\\-\\s]{7,25}$",
             message = "Phone number format is invalid")
    @Schema(example = "+91-9876543210", description = "Mobile number — required")
    private String phone;

    @NotBlank(message = "Initials are required")
    @Size(max = 10, message = "Initials must not exceed 10 characters")
    @Pattern(regexp = "^[A-Za-z]{1,10}$",
             message = "Initials may only contain letters (e.g. JKD)")
    @Schema(example = "JKD",
            description = "Short identifier used on signed pharma documents")
    private String initials;

    @NotNull(message = "Joining date is required")
    @PastOrPresent(message = "Joining date cannot be in the future")
    @Schema(example = "2024-04-15")
    private LocalDate joiningDate;

    /**
     * Legacy free-text department label. Optional — newer clients should
     * supply {@link #departmentId} instead.
     */
    @Size(max = 100, message = "Department must not exceed 100 characters")
    @Schema(example = "Quality Assurance",
            description = "Deprecated free-text label. Prefer departmentId.")
    private String department;

    @NotNull(message = "Department is required")
    @Schema(example = "5",
            description = "FK to departments.id — drives org-position checks")
    private Long departmentId;

    /** Mark this user as a department-level reviewer (cross-functional comments). */
    @Schema(example = "false")
    private Boolean isDeptReviewer;

    /** Mark this user as a QA Reviewer (only meaningful when departmentId is the QA department). */
    @Schema(example = "false")
    private Boolean isQaReviewer;

    @Size(max = 100, message = "Designation must not exceed 100 characters")
    @Schema(example = "QA Manager")
    private String designation;

    @Size(max = 50, message = "Employee ID must not exceed 50 characters")
    @Schema(example = "EMP-2024-001")
    private String employeeId;

    /** Role IDs to assign immediately on creation */
    @Schema(description = "Set of role IDs to assign. Defaults to EMPLOYEE role if empty.")
    private Set<Long> roleIds;
}
