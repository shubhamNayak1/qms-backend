package com.qms.module.org.service;

import com.qms.module.license.service.LicenseService;
import com.qms.module.org.dto.response.OrgTreeResponse;
import com.qms.module.org.dto.response.OrgTreeResponse.DeptNode;
import com.qms.module.org.dto.response.OrgTreeResponse.SiteNode;
import com.qms.module.org.dto.response.OrgTreeResponse.UserNode;
import com.qms.module.org.entity.Department;
import com.qms.module.org.entity.Site;
import com.qms.module.org.repository.DepartmentRepository;
import com.qms.module.org.repository.SiteRepository;
import com.qms.module.user.entity.User;
import com.qms.module.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds a single nested tree of the entire organisation.
 *
 * Goes Site → top-level Departments → Sub-Departments → Users in each.
 * Designed for the front-end org-chart view (top-down).
 *
 * Performance note: this is meant for an admin / dashboard screen and not
 * a hot-path API. Acceptable to do a few in-memory joins.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrgTreeService {

    private final SiteRepository       siteRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository       userRepository;
    private final LicenseService       licenseService;

    public OrgTreeResponse build() {
        Site site = siteRepository.findFirstByIsDeletedFalseOrderByIdAsc()
                .orElseThrow(() -> new IllegalStateException(
                        "No active site configured — run V18 migration."));

        List<Department> allDepts = departmentRepository.findAllByIsDeletedFalseOrderByNameAsc();

        // Group children by their parentId (null parents = top-level)
        Map<Long, List<Department>> byParent = allDepts.stream()
                .filter(d -> d.getParentId() != null)
                .collect(Collectors.groupingBy(Department::getParentId));

        List<Department> topLevel = allDepts.stream()
                .filter(d -> d.getParentId() == null)
                .toList();

        List<DeptNode> deptNodes = new ArrayList<>();
        for (Department top : topLevel) {
            deptNodes.add(buildDeptNode(top, byParent));
        }

        return OrgTreeResponse.builder()
                .site(SiteNode.builder()
                        .id(site.getId())
                        .name(site.getName())
                        .code(site.getCode())
                        .address(site.getAddress())
                        .head(site.getHeadUserId() == null
                                ? null
                                : userRepository.findById(site.getHeadUserId())
                                    .map(this::toUserNode).orElse(null))
                        .departments(deptNodes)
                        .build())
                .build();
    }

    // ─────────────────────────────────────────────────────────
    //  Internals
    // ─────────────────────────────────────────────────────────

    private DeptNode buildDeptNode(Department dept,
                                    Map<Long, List<Department>> byParent) {
        List<User> deptUsers = userRepository
                .findAllByDepartmentIdAndIsDeletedFalseOrderByFirstNameAsc(dept.getId());

        // Separate the HOD from regular members.
        UserNode hodNode = null;
        List<UserNode> memberNodes = new ArrayList<>();
        for (User u : deptUsers) {
            UserNode node = toUserNode(u);
            if (u.getId().equals(dept.getHodUserId())) {
                node.setIsHod(true);
                hodNode = node;
            } else {
                memberNodes.add(node);
            }
        }

        // Recurse into sub-departments.
        List<DeptNode> subs = new ArrayList<>();
        List<Department> children = byParent.getOrDefault(dept.getId(), List.of());
        int totalMemberCount = deptUsers.size();
        for (Department child : children) {
            DeptNode childNode = buildDeptNode(child, byParent);
            subs.add(childNode);
            totalMemberCount += (childNode.getTotalMemberCount() == null ? 0 : childNode.getTotalMemberCount());
        }

        return DeptNode.builder()
                .id(dept.getId())
                .name(dept.getName())
                .code(dept.getCode())
                .deptType(dept.getDeptType())
                .hod(hodNode)
                .members(memberNodes)
                .subDepartments(subs)
                .totalMemberCount(totalMemberCount)
                .build();
    }

    private UserNode toUserNode(User u) {
        return UserNode.builder()
                .id(u.getId())
                .username(u.getUsername())
                .fullName(u.getFullName())
                .initials(u.getInitials())
                .designation(u.getDesignation())
                .email(u.getEmail())
                .phone(u.getPhone())
                .joiningDate(u.getJoiningDate())
                .isHod(false)
                .isDeptReviewer(u.getIsDeptReviewer())
                .isQaReviewer(u.getIsQaReviewer())
                .hasActiveLicense(licenseService.userHasActiveLicense(u.getId()))
                .build();
    }
}
