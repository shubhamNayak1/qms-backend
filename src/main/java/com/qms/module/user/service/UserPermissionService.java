package com.qms.module.user.service;

import com.qms.common.exception.AppException;
import com.qms.module.user.dto.request.UserPermissionOverrideRequest;
import com.qms.module.user.dto.response.UserPermissionOverrideResponse;
import com.qms.module.user.entity.Permission;
import com.qms.module.user.entity.UserPermission;
import com.qms.module.user.repository.PermissionRepository;
import com.qms.module.user.repository.UserPermissionRepository;
import com.qms.module.user.repository.UserRepository;
import com.qms.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * UserPermissionService — grant/revoke overrides on top of role-based
 * permissions. Called from the Admin Permission matrix UI.
 *
 * Writes land in user_permissions; a login (or /auth/refresh) picks up
 * the new effective set via CustomUserDetailsService.
 */
@Service
@RequiredArgsConstructor
public class UserPermissionService {

    private final UserPermissionRepository userPermissionRepository;
    private final UserRepository           userRepository;
    private final PermissionRepository     permissionRepository;

    @Transactional(readOnly = true)
    public List<UserPermissionOverrideResponse> listForUser(Long userId) {
        requireUser(userId);
        List<UserPermission> rows = userPermissionRepository.findByUserId(userId);
        if (rows.isEmpty()) return List.of();
        Map<Long, Permission> byId = permissionRepository
                .findAllById(rows.stream().map(UserPermission::getPermissionId).toList())
                .stream().collect(Collectors.toMap(Permission::getId, p -> p));
        return rows.stream().map(r -> toResponse(r, byId.get(r.getPermissionId()))).toList();
    }

    @Transactional
    public UserPermissionOverrideResponse upsert(Long userId, UserPermissionOverrideRequest req) {
        requireUser(userId);
        Permission perm = permissionRepository.findById(req.getPermissionId())
                .orElseThrow(() -> AppException.notFound("Permission", req.getPermissionId()));
        UserPermission.GrantType type;
        try {
            type = UserPermission.GrantType.valueOf(req.getGrantType());
        } catch (IllegalArgumentException ex) {
            throw AppException.badRequest("grantType must be GRANT or REVOKE, got: " + req.getGrantType());
        }
        String actor = SecurityUtils.getCurrentUsername().orElse("SYSTEM");
        UserPermission row = userPermissionRepository
                .findById(new UserPermission.PK(userId, perm.getId()))
                .orElseGet(() -> UserPermission.builder()
                        .userId(userId)
                        .permissionId(perm.getId())
                        .build());
        row.setGrantType(type);
        row.setGrantedBy(actor);
        row.setGrantedAt(LocalDateTime.now());
        row.setReason(req.getReason());
        return toResponse(userPermissionRepository.save(row), perm);
    }

    @Transactional
    public void remove(Long userId, Long permissionId) {
        requireUser(userId);
        userPermissionRepository.deleteByUserIdAndPermissionId(userId, permissionId);
    }

    private void requireUser(Long userId) {
        if (!userRepository.existsById(userId))
            throw AppException.notFound("User", userId);
    }

    private UserPermissionOverrideResponse toResponse(UserPermission row, Permission perm) {
        return UserPermissionOverrideResponse.builder()
                .userId(row.getUserId())
                .permissionId(row.getPermissionId())
                .permissionName(perm != null ? perm.getName() : null)
                .permissionDisplay(perm != null ? perm.getDisplayName() : null)
                .module(perm != null ? perm.getModule() : null)
                .grantType(row.getGrantType().name())
                .grantedBy(row.getGrantedBy())
                .grantedAt(row.getGrantedAt())
                .reason(row.getReason())
                .build();
    }
}
