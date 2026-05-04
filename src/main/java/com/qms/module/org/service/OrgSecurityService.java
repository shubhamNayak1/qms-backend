package com.qms.module.org.service;

import com.qms.common.exception.AppException;
import com.qms.module.org.entity.Department;
import com.qms.module.org.entity.Site;
import com.qms.module.org.enums.DepartmentType;
import com.qms.module.org.repository.DepartmentRepository;
import com.qms.module.org.repository.SiteRepository;
import com.qms.module.user.entity.User;
import com.qms.module.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * OrgSecurityService — single source of truth for positional authorization
 * decisions on top of the org tree.
 *
 * "Positional" means: the actor must hold a specific structural role at the
 * time of the check (HOD of the originating department, member of the QA
 * department, the site head, etc.) — not just any user with a flat role.
 *
 * Used by:
 *   • {@code QmsWorkflowEngine} — validates that the actor satisfies the
 *     position required by the requested status transition.
 *   • Controllers — via {@code @PreAuthorize("@orgSecurity.isQaHead(...)")}.
 *
 * SUPER_ADMIN bypasses every positional check.
 */
@Slf4j
// Bean name is explicit so the SpEL expressions in @PreAuthorize annotations
// (e.g. "hasRole(...) or @orgSecurity.isCurrentUserQaHead()") resolve to this
// service. Spring's default name would be "orgSecurityService" — using the
// shorter alias keeps annotations readable across every controller.
@Service("orgSecurity")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrgSecurityService {

    private static final String SUPER_ADMIN = "ROLE_SUPER_ADMIN";

    private final UserRepository       userRepository;
    private final DepartmentRepository departmentRepository;
    private final SiteRepository       siteRepository;

    // ─────────────────────────────────────────────────────────
    //  Current-user helpers
    // ─────────────────────────────────────────────────────────

    /** Returns the currently authenticated user, or empty if anonymous. */
    public Optional<User> currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return Optional.empty();
        return userRepository.findByUsernameAndIsDeletedFalse(auth.getName());
    }

    public boolean isSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        for (GrantedAuthority a : auth.getAuthorities()) {
            if (SUPER_ADMIN.equals(a.getAuthority())) return true;
        }
        return false;
    }

    // ─────────────────────────────────────────────────────────
    //  Positional checks
    // ─────────────────────────────────────────────────────────

    /** True when {@code userId} is the HOD of the department with id = deptId. */
    public boolean isHodOfDepartment(Long userId, Long deptId) {
        if (userId == null || deptId == null) return false;
        return departmentRepository.findByIdAndIsDeletedFalse(deptId)
                .map(d -> userId.equals(d.getHodUserId()))
                .orElse(false);
    }

    /** True when the current authenticated user is the HOD of the given dept. */
    public boolean isCurrentUserHodOf(Long deptId) {
        if (isSuperAdmin()) return true;
        return currentUser().map(u -> isHodOfDepartment(u.getId(), deptId)).orElse(false);
    }

    /**
     * True when the current user is the QA Head — i.e. HOD of any active
     * department flagged as dept_type = QA.
     */
    public boolean isCurrentUserQaHead() {
        if (isSuperAdmin()) return true;
        return currentUser().map(this::isUserQaHead).orElse(false);
    }

    public boolean isUserQaHead(User user) {
        if (user == null || user.getId() == null) return false;
        for (Department qa : departmentRepository.findAllByDeptTypeAndIsDeletedFalse(DepartmentType.QA)) {
            if (user.getId().equals(qa.getHodUserId())) return true;
        }
        return false;
    }

    /**
     * True when the current user is a QA Reviewer — flagged is_qa_reviewer
     * AND a member of a QA-typed department.
     */
    public boolean isCurrentUserQaReviewer() {
        if (isSuperAdmin()) return true;
        return currentUser().map(this::isUserQaReviewer).orElse(false);
    }

    public boolean isUserQaReviewer(User user) {
        if (user == null || !Boolean.TRUE.equals(user.getIsQaReviewer())) return false;
        if (user.getDepartmentId() == null) return false;
        return departmentRepository.findByIdAndIsDeletedFalse(user.getDepartmentId())
                .map(d -> d.getDeptType() == DepartmentType.QA)
                .orElse(false);
    }

    /** True when the current user is a member of any RA-typed department. */
    public boolean isCurrentUserRa() {
        if (isSuperAdmin()) return true;
        return currentUser().map(this::isUserRa).orElse(false);
    }

    public boolean isUserRa(User user) {
        if (user == null || user.getDepartmentId() == null) return false;
        return departmentRepository.findByIdAndIsDeletedFalse(user.getDepartmentId())
                .map(d -> d.getDeptType() == DepartmentType.RA)
                .orElse(false);
    }

    /** True when the current user is the Site Head of any active site. */
    public boolean isCurrentUserSiteHead() {
        if (isSuperAdmin()) return true;
        return currentUser().map(this::isUserSiteHead).orElse(false);
    }

    public boolean isUserSiteHead(User user) {
        if (user == null || user.getId() == null) return false;
        return siteRepository.findFirstByIsDeletedFalseOrderByIdAsc()
                .map(s -> user.getId().equals(s.getHeadUserId()))
                .orElse(false);
    }

    /** True when the current user is a designated department reviewer. */
    public boolean isCurrentUserDeptReviewerOf(Long deptId) {
        if (isSuperAdmin()) return true;
        return currentUser()
                .map(u -> Boolean.TRUE.equals(u.getIsDeptReviewer())
                        && deptId != null
                        && deptId.equals(u.getDepartmentId()))
                .orElse(false);
    }

    // ─────────────────────────────────────────────────────────
    //  Lookups used by other services
    // ─────────────────────────────────────────────────────────

    public Department requireDepartment(Long deptId) {
        return departmentRepository.findByIdAndIsDeletedFalse(deptId)
                .orElseThrow(() -> AppException.notFound("Department", deptId));
    }

    public Site requireDefaultSite() {
        return siteRepository.findFirstByIsDeletedFalseOrderByIdAsc()
                .orElseThrow(() -> AppException.internalError(
                        "No active site is configured. Run V18 migration."));
    }
}
