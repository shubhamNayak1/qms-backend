package com.qms.module.org.service;

import com.qms.common.exception.AppException;
import com.qms.module.org.dto.request.DepartmentRequest;
import com.qms.module.org.dto.response.DepartmentResponse;
import com.qms.module.org.entity.Department;
import com.qms.module.org.repository.DepartmentRepository;
import com.qms.module.user.entity.User;
import com.qms.module.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final UserRepository       userRepository;

    public List<DepartmentResponse> listAll() {
        return departmentRepository.findAllByIsDeletedFalseOrderByNameAsc()
                .stream().map(this::toResponse).toList();
    }

    public DepartmentResponse getById(Long id) {
        return toResponse(require(id));
    }

    @Transactional
    public DepartmentResponse create(DepartmentRequest req) {
        if (departmentRepository.existsByCodeAndIsDeletedFalse(req.getCode())) {
            throw AppException.conflict(
                    "Department with code '" + req.getCode() + "' already exists.");
        }
        validateParent(req.getParentId(), null);
        validateHod(req.getHodUserId());

        Department d = Department.builder()
                .name(req.getName())
                .code(req.getCode())
                .description(req.getDescription())
                .siteId(req.getSiteId())
                .parentId(req.getParentId())
                .hodUserId(req.getHodUserId())
                .deptType(req.getDeptType())
                .isActive(true)
                .build();
        Department saved = departmentRepository.save(d);
        log.info("Department created: {} ({}, type={})", saved.getName(), saved.getCode(), saved.getDeptType());
        return toResponse(saved);
    }

    @Transactional
    public DepartmentResponse update(Long id, DepartmentRequest req) {
        Department d = require(id);
        if (!d.getCode().equals(req.getCode())
                && departmentRepository.existsByCodeAndIsDeletedFalse(req.getCode())) {
            throw AppException.conflict(
                    "Department with code '" + req.getCode() + "' already exists.");
        }
        validateParent(req.getParentId(), id);
        validateHod(req.getHodUserId());

        d.setName(req.getName());
        d.setCode(req.getCode());
        d.setDescription(req.getDescription());
        d.setSiteId(req.getSiteId());
        d.setParentId(req.getParentId());
        d.setHodUserId(req.getHodUserId());
        d.setDeptType(req.getDeptType());
        return toResponse(departmentRepository.save(d));
    }

    @Transactional
    public void softDelete(Long id) {
        Department d = require(id);
        // Reassign children to grandparent so we don't orphan the tree.
        List<Department> kids = departmentRepository
                .findAllByParentIdAndIsDeletedFalseOrderByNameAsc(id);
        for (Department k : kids) {
            k.setParentId(d.getParentId());
            departmentRepository.save(k);
        }
        d.setIsDeleted(true);
        d.setIsActive(false);
        departmentRepository.save(d);
        log.info("Department soft-deleted: {} (id={}). {} sub-depts re-parented.",
                d.getCode(), id, kids.size());
    }

    // ─────────────────────────────────────────────────────────
    //  Internals
    // ─────────────────────────────────────────────────────────

    private Department require(Long id) {
        return departmentRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> AppException.notFound("Department", id));
    }

    /** Prevents cycles when assigning a parent. */
    private void validateParent(Long parentId, Long selfId) {
        if (parentId == null) return;
        if (selfId != null && parentId.equals(selfId)) {
            throw AppException.badRequest("A department cannot be its own parent.");
        }
        Long current = parentId;
        int hops = 0;
        while (current != null && hops < 25) {
            if (selfId != null && current.equals(selfId)) {
                throw AppException.badRequest(
                        "Circular department parent — would create a cycle.");
            }
            current = departmentRepository.findByIdAndIsDeletedFalse(current)
                    .map(Department::getParentId).orElse(null);
            hops++;
        }
    }

    private void validateHod(Long hodUserId) {
        if (hodUserId == null) return;
        userRepository.findByIdAndIsDeletedFalse(hodUserId)
                .orElseThrow(() -> AppException.notFound("User", hodUserId));
    }

    private DepartmentResponse toResponse(Department d) {
        Optional<Department> parent = (d.getParentId() == null) ? Optional.empty()
                : departmentRepository.findByIdAndIsDeletedFalse(d.getParentId());
        String hodName = null;
        if (d.getHodUserId() != null) {
            hodName = userRepository.findById(d.getHodUserId())
                    .map(User::getFullName).orElse(null);
        }
        int memberCount = userRepository
                .findAllByDepartmentIdAndIsDeletedFalseOrderByFirstNameAsc(d.getId()).size();

        return DepartmentResponse.builder()
                .id(d.getId())
                .name(d.getName())
                .code(d.getCode())
                .description(d.getDescription())
                .siteId(d.getSiteId())
                .parentId(d.getParentId())
                .parentName(parent.map(Department::getName).orElse(null))
                .hodUserId(d.getHodUserId())
                .hodUserName(hodName)
                .deptType(d.getDeptType())
                .isActive(d.getIsActive())
                .memberCount(memberCount)
                .createdAt(d.getCreatedAt())
                .build();
    }
}
