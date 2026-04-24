package com.qms.module.user.service;

import com.qms.common.enums.AuditAction;
import com.qms.common.enums.AuditModule;
import com.qms.common.exception.AppException;
import com.qms.common.response.PageResponse;
import com.qms.module.audit.annotation.Audited;
import com.qms.module.audit.context.AuditContext;
import com.qms.module.audit.context.AuditContextHolder;
import com.qms.module.audit.service.AuditValueSerializer;
import com.qms.module.user.dto.request.*;
import com.qms.module.user.dto.response.MeResponse;
import com.qms.module.user.dto.response.UserResponse;
import com.qms.module.user.entity.Permission;
import com.qms.module.user.entity.Role;
import com.qms.module.user.entity.User;
import com.qms.module.user.mapper.UserMapper;
import com.qms.module.user.repository.RoleRepository;
import com.qms.module.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.qms.module.user.repository.UserSpecification;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository       userRepository;
    private final RoleRepository       roleRepository;
    private final UserMapper           userMapper;
    private final PasswordEncoder      passwordEncoder;
    private final AuditValueSerializer auditSerializer;

    // ─── Queries ─────────────────────────────────────────────

    public PageResponse<UserResponse> getAll(String search, String department,
                                              Boolean isActive, int page, int size) {
        Specification<User> spec = UserSpecification.filter(search, department, isActive);
        var pageResult = userRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return PageResponse.of(pageResult.map(userMapper::toUserResponse));
    }

    public UserResponse getById(Long id) {
        return userMapper.toUserResponse(findById(id));
    }

    public UserResponse getByUsername(String username) {
        User user = userRepository.findByUsernameAndIsDeletedFalse(username)
                .orElseThrow(() -> AppException.notFound("User", username));
        return userMapper.toUserResponse(user);
    }

    // ─── Commands ────────────────────────────────────────────

    @Audited(action = AuditAction.CREATE, module = AuditModule.USER,
             entityType = "User", description = "User account created")
    @Transactional
    public UserResponse create(CreateUserRequest req) {
        // Uniqueness guards
        if (userRepository.existsByUsernameAndIsDeletedFalse(req.getUsername())) {
            throw AppException.conflict(
                    "Username '" + req.getUsername() + "' is already taken.");
        }
        if (userRepository.existsByEmailAndIsDeletedFalse(req.getEmail())) {
            throw AppException.conflict(
                    "Email '" + req.getEmail() + "' is already registered.");
        }
        if (req.getEmployeeId() != null
                && userRepository.existsByEmployeeIdAndIsDeletedFalse(req.getEmployeeId())) {
            throw AppException.conflict(
                    "Employee ID '" + req.getEmployeeId() + "' is already in use.");
        }

        Set<Role> roles = resolveRoles(req.getRoleIds());

        User user = User.builder()
                .username(req.getUsername())
                .email(req.getEmail().toLowerCase())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .firstName(req.getFirstName())
                .lastName(req.getLastName())
                .phone(req.getPhone())
                .department(req.getDepartment())
                .designation(req.getDesignation())
                .employeeId(req.getEmployeeId())
                .isActive(true)
                .isEmailVerified(false)
                .failedLoginAttempts(0)
                .roles(roles)
                .build();

        User saved = userRepository.save(user);
        log.info("User created: {} (id={})", saved.getUsername(), saved.getId());
        return userMapper.toUserResponse(saved);
    }

    @Audited(action = AuditAction.UPDATE, module = AuditModule.USER,
             entityType = "User", entityIdArgIndex = 0)
    @Transactional
    public UserResponse update(Long id, UpdateUserRequest req) {
        User user = findById(id);
        AuditContextHolder.set(AuditContext.builder()
                .oldValue(auditSerializer.serialize(userMapper.toUserResponse(user)))
                .build());

        if (req.getEmployeeId() != null
                && !req.getEmployeeId().equals(user.getEmployeeId())
                && userRepository.existsByEmployeeIdAndIsDeletedFalse(req.getEmployeeId())) {
            throw AppException.conflict(
                    "Employee ID '" + req.getEmployeeId() + "' is already in use.");
        }

        if (req.getFirstName()        != null) user.setFirstName(req.getFirstName());
        if (req.getLastName()         != null) user.setLastName(req.getLastName());
        if (req.getPhone()            != null) user.setPhone(req.getPhone());
        if (req.getDepartment()       != null) user.setDepartment(req.getDepartment());
        if (req.getDesignation()      != null) user.setDesignation(req.getDesignation());
        if (req.getEmployeeId()       != null) user.setEmployeeId(req.getEmployeeId());
        if (req.getProfilePictureUrl() != null) user.setProfilePictureUrl(req.getProfilePictureUrl());
        if (req.getIsActive()         != null) user.setIsActive(req.getIsActive());

        return userMapper.toUserResponse(userRepository.save(user));
    }

    /**
     * Replaces the user's entire role set.
     * Transactional — roles are swapped atomically.
     */
    @Audited(action = AuditAction.ROLE_ASSIGNED, module = AuditModule.USER,
             entityType = "User", entityIdArgIndex = 0)
    @Transactional
    public UserResponse assignRoles(Long userId, AssignRolesRequest req) {
        User user = findById(userId);
        Set<Role> newRoles = resolveRoles(req.getRoleIds());

        user.clearRoles();
        newRoles.forEach(user::addRole);

        User saved = userRepository.save(user);
        log.info("Roles updated for user '{}': {}", saved.getUsername(),
                newRoles.stream().map(Role::getName).toList());
        return userMapper.toUserResponse(saved);
    }

    @Audited(action = AuditAction.PASSWORD_CHANGED, module = AuditModule.USER,
             entityType = "User", entityIdArgIndex = 0, captureNewValue = false)
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest req) {
        User user = findById(userId);

        if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPasswordHash())) {
            throw AppException.badRequest("Current password is incorrect.");
        }
        if (!req.getNewPassword().equals(req.getConfirmPassword())) {
            throw AppException.badRequest("New password and confirm password do not match.");
        }
        if (passwordEncoder.matches(req.getNewPassword(), user.getPasswordHash())) {
            throw AppException.badRequest(
                    "New password must be different from the current password.");
        }

        userRepository.updatePassword(userId,
                passwordEncoder.encode(req.getNewPassword()),
                LocalDateTime.now());

        log.info("Password changed for user '{}'", user.getUsername());
    }

    @Audited(action = AuditAction.PASSWORD_RESET_COMPLETED, module = AuditModule.USER,
             entityType = "User", entityIdArgIndex = 0, captureNewValue = false,
             description = "Admin reset user password")
    @Transactional
    public void adminResetPassword(Long userId, String newPassword) {
        User user = findById(userId);
        userRepository.updatePassword(userId,
                passwordEncoder.encode(newPassword), LocalDateTime.now());
        log.info("Admin reset password for user '{}'", user.getUsername());
    }

    @Transactional
    public void initiateForgotPassword(String email) {
        User user = userRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() ->
                        // Deliberately vague — don't reveal whether email exists
                        AppException.notFound(
                                "If that email is registered, a reset link will be sent."));

        String token = UUID.randomUUID().toString();
        // Store the plain UUID — it is already 128-bit random, so BCrypt provides no benefit
        // and would prevent direct DB lookup via findByPasswordResetToken().
        user.setPasswordResetToken(token);
        user.setPasswordResetTokenExpiry(LocalDateTime.now().plusHours(2));
        userRepository.save(user);

        // TODO: wire up email service and remove this log statement before shipping
        // emailService.sendPasswordResetEmail(user.getEmail(), token);
        log.info("Password reset initiated for '{}' — token must be delivered via email only",
                user.getEmail());
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        if (!req.getNewPassword().equals(req.getConfirmPassword())) {
            throw AppException.badRequest("Passwords do not match.");
        }

        // Direct indexed lookup — token is stored as a plain UUID (not BCrypt-hashed)
        // so we can look it up efficiently rather than scanning all rows.
        User user = userRepository.findByPasswordResetTokenAndIsDeletedFalse(req.getToken())
                .filter(u -> u.getPasswordResetTokenExpiry() != null
                        && u.getPasswordResetTokenExpiry().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> AppException.badRequest(
                        "Password reset token is invalid or has expired."));

        userRepository.updatePassword(user.getId(),
                passwordEncoder.encode(req.getNewPassword()), LocalDateTime.now());

        user.setPasswordResetToken(null);
        user.setPasswordResetTokenExpiry(null);
        userRepository.save(user);

        log.info("Password successfully reset for '{}'", user.getUsername());
    }

    @Audited(action = AuditAction.UPDATE, module = AuditModule.USER,
             entityType = "User", entityIdArgIndex = 0, captureNewValue = false,
             description = "User account activated")
    @Transactional
    public void activateUser(Long id) {
        findById(id); // ensures user exists
        userRepository.updateActiveStatus(id, true);
        log.info("User id={} activated", id);
    }

    @Audited(action = AuditAction.UPDATE, module = AuditModule.USER,
             entityType = "User", entityIdArgIndex = 0, captureNewValue = false,
             description = "User account deactivated")
    @Transactional
    public void deactivateUser(Long id) {
        findById(id);
        userRepository.updateActiveStatus(id, false);
        log.info("User id={} deactivated", id);
    }

    @Audited(action = AuditAction.DELETE, module = AuditModule.USER,
             entityType = "User", entityIdArgIndex = 0, captureNewValue = false,
             description = "User account deleted")
    @Transactional
    public void softDelete(Long id) {
        User user = findById(id);
        AuditContextHolder.set(AuditContext.builder()
                .oldValue(auditSerializer.serialize(userMapper.toUserResponse(user)))
                .build());
        user.setIsDeleted(true);
        user.setIsActive(false);
        userRepository.save(user);
        log.info("User soft-deleted: {} (id={})", user.getUsername(), id);
    }

    // ─── /me — current authenticated user ────────────────────

    /**
     * Returns the profile and full permission payload for the currently
     * authenticated user.  Permissions are delivered in three shapes:
     *   • permissionSet          – flat Set for O(1) contains() checks
     *   • permissionsByModule    – grouped map for page-level gating
     *   • moduleAccess           – boolean per module for nav-item visibility
     */
    public MeResponse getMe() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsernameAndIsDeletedFalse(username)
                .orElseThrow(() -> AppException.notFound("User", username));

        // Union of all permissions across every assigned role
        Set<Permission> allPerms = user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .collect(Collectors.toSet());

        // Flat name set — for quick front-end checks
        Set<String> permissionSet = allPerms.stream()
                .map(Permission::getName)
                .collect(Collectors.toSet());

        // Grouped by module — for show/hide at page level
        Map<String, List<String>> permissionsByModule = allPerms.stream()
                .collect(Collectors.groupingBy(
                        Permission::getModule,
                        Collectors.mapping(Permission::getName, Collectors.toList())
                ));

        // Boolean per module — for nav-item visibility
        Map<String, Boolean> moduleAccess = new LinkedHashMap<>();
        for (String mod : List.of("USER", "QMS", "DMS", "LMS", "REPORT", "AUDIT")) {
            moduleAccess.put(mod, permissionsByModule.containsKey(mod));
        }

        Set<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());

        return MeResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phone(user.getPhone())
                .department(user.getDepartment())
                .designation(user.getDesignation())
                .employeeId(user.getEmployeeId())
                .profilePictureUrl(user.getProfilePictureUrl())
                .isActive(user.getIsActive())
                .lastLoginAt(user.getLastLoginAt())
                .roles(roles)
                .permissionSet(permissionSet)
                .permissionsByModule(permissionsByModule)
                .moduleAccess(moduleAccess)
                .build();
    }

    // ─── Internal helpers ────────────────────────────────────

    User findById(Long id) {
        return userRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> AppException.notFound("User", id));
    }

    private Set<Role> resolveRoles(Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            // Default to EMPLOYEE role
            return roleRepository.findByNameAndIsDeletedFalse("EMPLOYEE")
                    .map(r -> (Set<Role>) new java.util.HashSet<>(Set.of(r)))
                    .orElse(Collections.emptySet());
        }

        Set<Role> roles = roleRepository.findAllByIdInAndIsDeletedFalse(roleIds);
        if (roles.size() != roleIds.size()) {
            throw AppException.badRequest(
                    "One or more role IDs are invalid or do not exist.");
        }
        return roles;
    }
}
