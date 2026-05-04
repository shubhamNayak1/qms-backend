package com.qms.module.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "Request body for updating an existing user. Null fields are ignored.")
public class UpdateUserRequest {

    @Size(min = 1, max = 80, message = "First name must not exceed 80 characters")
    private String firstName;

    @Size(min = 1, max = 80, message = "Last name must not exceed 80 characters")
    private String lastName;

    @Pattern(regexp = "^\\+?[0-9\\-\\s]{7,25}$", message = "Phone number format is invalid")
    private String phone;

    @Size(max = 10)
    @Pattern(regexp = "^[A-Za-z]{1,10}$",
             message = "Initials may only contain letters")
    private String initials;

    @PastOrPresent(message = "Joining date cannot be in the future")
    private LocalDate joiningDate;

    @Size(max = 100)
    private String department;

    private Long departmentId;

    private Boolean isDeptReviewer;

    private Boolean isQaReviewer;

    @Size(max = 100)
    private String designation;

    @Size(max = 50)
    private String employeeId;

    @Size(max = 500)
    private String profilePictureUrl;

    private Boolean isActive;
}
