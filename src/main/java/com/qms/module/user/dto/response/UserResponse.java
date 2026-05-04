package com.qms.module.user.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

@Data
@Builder
public class UserResponse {
    private Long    id;
    private String  username;
    private String  email;
    private String  firstName;
    private String  lastName;
    private String  fullName;
    private String  initials;
    private String  phone;
    private LocalDate joiningDate;
    private String  department;          // legacy free-text label
    private Long    departmentId;
    private String  departmentName;      // resolved on read
    private Boolean isDeptReviewer;
    private Boolean isQaReviewer;
    private String  designation;
    private String  employeeId;
    private String  profilePictureUrl;
    private Boolean isActive;
    private Boolean isEmailVerified;
    private Boolean hasActiveLicense;
    private String  licenseCode;          // current assigned license code, if any
    private Set<RoleResponse> roles;
    private Set<String> permissions;  // flat set of all permission names
    private LocalDateTime lastLoginAt;
    private LocalDateTime passwordChangedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String  createdBy;
    private boolean disabled;
}
