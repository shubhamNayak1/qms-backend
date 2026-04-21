package com.qms.module.user.dto.request;

import com.qms.module.user.validator.ValidPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

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

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    @Size(max = 150, message = "Email must not exceed 150 characters")
    @Schema(example = "john.doe@company.com")
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

    @NotBlank(message = "Last name is required")
    @Size(min = 1, max = 80, message = "Last name must not exceed 80 characters")
    @Schema(example = "Doe")
    private String lastName;

    @Pattern(regexp = "^\\+?[0-9\\-\\s]{7,25}$",
             message = "Phone number format is invalid")
    @Schema(example = "+91-9876543210")
    private String phone;

    @Size(max = 100, message = "Department must not exceed 100 characters")
    @Schema(example = "Quality Assurance")
    private String department;

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
