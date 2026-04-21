package com.qms.module.user.service;

import com.qms.common.enums.AuditAction;
import com.qms.common.enums.AuditModule;
import com.qms.common.exception.AppException;
import com.qms.common.response.PageResponse;
import com.qms.module.audit.annotation.Audited;
import com.qms.module.user.dto.request.CreatePermissionRequest;
import com.qms.module.user.dto.response.PermissionResponse;
import com.qms.module.user.entity.Permission;
import com.qms.module.user.entity.Role;
import com.qms.module.user.mapper.UserMapper;
import com.qms.module.user.repository.PermissionRepository;
import com.qms.module.user.repository.PermissionSpecification;
import com.qms.module.user.repository.RoleSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PermissionService {

    private final PermissionRepository permissionRepository;
    private final UserMapper           userMapper;

    // ─── Queries ─────────────────────────────────────────────

    public PageResponse<PermissionResponse> getAll(String module, String search,
                                                    int page, int size) {
        Specification<Permission> spec = PermissionSpecification.filter(module, search);

        var pageResult = permissionRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by("module", "name")));
        return PageResponse.of(pageResult.map(userMapper::toPermissionResponse));


//        var pageResult = permissionRepository.searchPermissions(
//                module, search,
//                PageRequest.of(page, size, Sort.by("module", "name")));
//        return PageResponse.of(pageResult.map(userMapper::toPermissionResponse));
    }

    public List<PermissionResponse> getAllFlat() {
        return permissionRepository.findAllByIsDeletedFalse()
                .stream()
                .map(userMapper::toPermissionResponse)
                .toList();
    }

    public PermissionResponse getById(Long id) {
        return userMapper.toPermissionResponse(findById(id));
    }

    // ─── Commands ────────────────────────────────────────────

    @Audited(action = AuditAction.CREATE, module = AuditModule.PERMISSION,
             entityType = "Permission", description = "Permission created")
    @Transactional
    public PermissionResponse create(CreatePermissionRequest req) {
        if (permissionRepository.existsByNameAndIsDeletedFalse(req.getName())) {
            throw AppException.conflict(
                    "Permission '" + req.getName() + "' already exists");
        }

        Permission permission = Permission.builder()
                .name(req.getName())
                .displayName(req.getDisplayName())
                .module(req.getModule().toUpperCase())
                .description(req.getDescription())
                .build();

        Permission saved = permissionRepository.save(permission);
        log.info("Permission created: {} (id={})", saved.getName(), saved.getId());
        return userMapper.toPermissionResponse(saved);
    }

    @Audited(action = AuditAction.UPDATE, module = AuditModule.PERMISSION,
             entityType = "Permission", entityIdArgIndex = 0)
    @Transactional
    public PermissionResponse update(Long id, CreatePermissionRequest req) {
        Permission permission = findById(id);

        // Check name uniqueness if changing the name
        if (!permission.getName().equals(req.getName())
                && permissionRepository.existsByNameAndIsDeletedFalse(req.getName())) {
            throw AppException.conflict("Permission name '" + req.getName() + "' is already taken");
        }

        if (req.getName()        != null) permission.setName(req.getName().toUpperCase());
        if (req.getDisplayName() != null) permission.setDisplayName(req.getDisplayName());
        if (req.getModule()      != null) permission.setModule(req.getModule().toUpperCase());
        if (req.getDescription() != null) permission.setDescription(req.getDescription());

        return userMapper.toPermissionResponse(permissionRepository.save(permission));
    }

    @Audited(action = AuditAction.DELETE, module = AuditModule.PERMISSION,
             entityType = "Permission", entityIdArgIndex = 0, captureNewValue = false,
             description = "Permission deleted")
    @Transactional
    public void delete(Long id) {
        Permission permission = findById(id);

        if (!permission.getRoles().isEmpty()) {
            throw AppException.conflict(
                    "Cannot delete permission '" + permission.getName()
                    + "' — it is assigned to " + permission.getRoles().size() + " role(s).");
        }

        permission.setIsDeleted(true);
        permissionRepository.save(permission);
        log.info("Permission soft-deleted: {} (id={})", permission.getName(), id);
    }

    // ─── Internal helper ────────────────────────────────────

    Permission findById(Long id) {
        return permissionRepository.findById(id)
                .filter(p -> !Boolean.TRUE.equals(p.getIsDeleted()))
                .orElseThrow(() -> AppException.notFound("Permission", id));
    }
}
