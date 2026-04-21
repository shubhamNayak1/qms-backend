package com.qms.module.user.service;

import com.qms.common.enums.AuditAction;
import com.qms.common.enums.AuditModule;
import com.qms.common.exception.AppException;
import com.qms.common.response.PageResponse;
import com.qms.module.audit.annotation.Audited;
import com.qms.module.user.dto.request.AssignPermissionsRequest;
import com.qms.module.user.dto.request.CreateRoleRequest;
import com.qms.module.user.dto.response.RoleResponse;
import com.qms.module.user.entity.Permission;
import com.qms.module.user.entity.Role;
import com.qms.module.user.mapper.UserMapper;
import com.qms.module.user.repository.PermissionRepository;
import com.qms.module.user.repository.RoleRepository;
import com.qms.module.user.repository.RoleSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Set;


@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoleService {

    private final RoleRepository       roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserMapper           userMapper;

    // ─── Queries ─────────────────────────────────────────────

    public PageResponse<RoleResponse> getAll(String search, int page, int size) {
        Specification<Role> spec = RoleSpecification.filter(search);
        var pageResult = roleRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));

        return PageResponse.of(pageResult.map(userMapper::toRoleResponse));
    }

    public List<RoleResponse> getAllFlat() {
        return roleRepository.findAllByIsDeletedFalse()
                .stream()
                .map(role -> enrichWithUserCount(userMapper.toRoleResponse(role), role.getId()))
                .toList();
    }

    public RoleResponse getById(Long id) {
        Role role = findById(id);
        return enrichWithUserCount(userMapper.toRoleResponse(role), role.getId());
    }

    // ─── Commands ────────────────────────────────────────────

    @Audited(action = AuditAction.CREATE, module = AuditModule.ROLE,
             entityType = "Role", description = "Role created")
    @Transactional
    public RoleResponse create(CreateRoleRequest req) {
        if (roleRepository.existsByNameAndIsDeletedFalse(req.getName())) {
            throw AppException.conflict("Role '" + req.getName() + "' already exists");
        }

        Set<Permission> permissions = resolvePermissions(req.getPermissionIds());

        Role role = Role.builder()
                .name(req.getName())
                .displayName(req.getDisplayName())
                .description(req.getDescription())
                .isSystemRole(false)
                .permissions(permissions)
                .build();

        Role saved = roleRepository.save(role);
        log.info("Role created: {} (id={})", saved.getName(), saved.getId());
        return enrichWithUserCount(userMapper.toRoleResponse(saved), saved.getId());
    }

    @Audited(action = AuditAction.UPDATE, module = AuditModule.ROLE,
             entityType = "Role", entityIdArgIndex = 0)
    @Transactional
    public RoleResponse update(Long id, CreateRoleRequest req) {
        Role role = findById(id);

        if (!role.getName().equals(req.getName())
                && roleRepository.existsByNameAndIsDeletedFalse(req.getName())) {
            throw AppException.conflict("Role name '" + req.getName() + "' is already taken");
        }

        if (req.getName()        != null) role.setName(req.getName());
        if (req.getDisplayName() != null) role.setDisplayName(req.getDisplayName());
        if (req.getDescription() != null) role.setDescription(req.getDescription());

        return enrichWithUserCount(
                userMapper.toRoleResponse(roleRepository.save(role)), role.getId());
    }

    /**
     * Replaces the entire permission set for a role.
     * Pass an empty set to revoke all permissions.
     */
    @Audited(action = AuditAction.PERMISSION_GRANTED, module = AuditModule.ROLE,
             entityType = "Role", entityIdArgIndex = 0, description = "Role permissions updated")
    @Transactional
    public RoleResponse assignPermissions(Long roleId, AssignPermissionsRequest req) {
        Role role = findById(roleId);

        Set<Permission> newPermissions = resolvePermissions(req.getPermissionIds());

        // Clear and replace — cleaner than diffing
        role.getPermissions().clear();
        role.getPermissions().addAll(newPermissions);

        Role saved = roleRepository.save(role);
        log.info("Permissions updated for role '{}': {} permission(s)",
                saved.getName(), newPermissions.size());

        return enrichWithUserCount(userMapper.toRoleResponse(saved), saved.getId());
    }

    @Audited(action = AuditAction.DELETE, module = AuditModule.ROLE,
             entityType = "Role", entityIdArgIndex = 0, captureNewValue = false,
             description = "Role deleted")
    @Transactional
    public void delete(Long id) {
        Role role = findById(id);

        if (Boolean.TRUE.equals(role.getIsSystemRole())) {
            throw AppException.forbidden(
                    "System role '" + role.getName() + "' cannot be deleted.");
        }

        long userCount = roleRepository.countUsersByRoleId(id);
        if (userCount > 0) {
            throw AppException.conflict(
                    "Cannot delete role '" + role.getName()
                    + "' — it is assigned to " + userCount + " user(s). "
                    + "Reassign or remove those users first.");
        }

        role.setIsDeleted(true);
        roleRepository.save(role);
        log.info("Role soft-deleted: {} (id={})", role.getName(), id);
    }

    // ─── Internal helpers ────────────────────────────────────

    Role findById(Long id) {
        return roleRepository.findById(id)
                .filter(r -> !Boolean.TRUE.equals(r.getIsDeleted()))
                .orElseThrow(() -> AppException.notFound("Role", id));
    }

    private Set<Permission> resolvePermissions(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) return Collections.emptySet();

        Set<Permission> found = permissionRepository.findAllByIdInAndIsDeletedFalse(ids);
        if (found.size() != ids.size()) {
            throw AppException.badRequest(
                    "One or more permission IDs are invalid or do not exist.");
        }
        return found;
    }

    private RoleResponse enrichWithUserCount(RoleResponse response, Long roleId) {
        long count = roleRepository.countUsersByRoleId(roleId);
        // RoleResponse is a @Builder class — rebuild with userCount populated
        return RoleResponse.builder()
                .id(response.getId())
                .name(response.getName())
                .displayName(response.getDisplayName())
                .description(response.getDescription())
                .isSystemRole(response.getIsSystemRole())
                .permissions(response.getPermissions())
                .userCount(count)
                .createdAt(response.getCreatedAt())
                .updatedAt(response.getUpdatedAt())
                .createdBy(response.getCreatedBy())
                .build();
    }
}
