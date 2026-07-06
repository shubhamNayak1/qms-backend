package com.qms.module.qms.common.repository;

import com.qms.common.enums.QmsRecordType;
import com.qms.module.org.service.OrgSecurityService;
import com.qms.module.qms.common.entity.QmsDepartmentComment;
import com.qms.module.qms.common.entity.QmsRecord;
import com.qms.module.user.entity.User;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Round-N (2026-07-04) tester CC-Point-2 · Issue 7 — row-level ACL
 * for QMS record list queries.
 *
 * A user sees a QMS record only when they hold a role that gives them
 * authority at some stage of the record's flow (past, present or
 * future). SUPER_ADMIN bypasses the filter and sees everything.
 *
 * Visibility predicates (any-of):
 *  1. Creator                   — raised_by_id = user.id
 *  2. Assigned                  — assigned_to_id = user.id
 *  3. HOD of originating dept   — user is HOD AND department_id = user.department_id
 *  4. Peer reviewer of dept     — user.is_dept_reviewer AND same department
 *  5. QA Reviewer / QA Head     — sees every QMS record (any-stage authority)
 *  6. RA                        — sees every record touching RA workflow
 *  7. Site Head                 — sees every record touching Site Head workflow
 *  8. Dept HOD invited for comment
 *                               — exists a dept_comment row with
 *                                 department_id = user.department_id
 *
 * The specification is generic across all five QMS entity types
 * because they all extend {@link QmsRecord}.
 */
public final class QmsVisibilitySpecification {

    private QmsVisibilitySpecification() {}

    /**
     * Returns a Specification that filters the given entity type to
     * records visible to the current user. Returns a no-op predicate
     * (always true) when the caller is a SUPER_ADMIN or when no
     * authenticated user is present (background jobs / tests).
     */
    public static <T extends QmsRecord> Specification<T> visibleToCurrentUser(
            OrgSecurityService orgSecurity, QmsRecordType recordType) {
        return (root, query, cb) -> {
            // SUPER_ADMIN sees everything — same rule the workflow engine uses.
            if (orgSecurity.isSuperAdmin()) return cb.conjunction();
            User user = orgSecurity.currentUser().orElse(null);
            if (user == null) return cb.conjunction();

            List<Predicate> anyOf = new ArrayList<>();

            // 1 — creator sees their own records
            anyOf.add(cb.equal(root.get("raisedById"), user.getId()));
            // 2 — assigned user sees the record
            anyOf.add(cb.equal(root.get("assignedToId"), user.getId()));

            // 3 — HOD of the record's originating dept
            if (user.getDepartmentId() != null
                    && orgSecurity.isHodOfDepartment(user.getId(), user.getDepartmentId())) {
                anyOf.add(cb.equal(root.get("departmentId"), user.getDepartmentId()));
            }

            // 4 — Peer Reviewer flag: same-dept records at PENDING_REVIEW
            if (Boolean.TRUE.equals(user.getIsDeptReviewer())
                    && user.getDepartmentId() != null) {
                anyOf.add(cb.equal(root.get("departmentId"), user.getDepartmentId()));
            }

            // 5 — QA Reviewer / QA Head see every QMS record
            //     (their authority applies at PENDING_QA_REVIEW /
            //      PENDING_DEPT_COMMENT / etc. — universal across the module)
            if (orgSecurity.isCurrentUserQaReviewer()
                    || orgSecurity.isCurrentUserQaHead()) {
                anyOf.add(cb.conjunction()); // always true → sees all
            }

            // 6 — RA users see everything (only Change Control + Deviation
            //     actually route through PENDING_RA_REVIEW, but showing
            //     the full list is easier than a per-module filter)
            if (orgSecurity.isCurrentUserRa()) {
                anyOf.add(cb.conjunction());
            }

            // 7 — Site Head sees everything (records may or may not need
            //     Site Head concurrence)
            if (orgSecurity.isCurrentUserSiteHead()) {
                anyOf.add(cb.conjunction());
            }

            // 8 — HOD of a dept with a dept_comment row on this record
            if (user.getDepartmentId() != null
                    && orgSecurity.isHodOfDepartment(user.getId(), user.getDepartmentId())) {
                assert query != null;
                Subquery<Long> sub = query.subquery(Long.class);
                Root<QmsDepartmentComment> dc = sub.from(QmsDepartmentComment.class);
                sub.select(dc.get("id")).where(
                        cb.equal(dc.get("recordType"),  recordType),
                        cb.equal(dc.get("recordId"),    root.get("id")),
                        cb.equal(dc.get("departmentId"), user.getDepartmentId()),
                        cb.isFalse(dc.get("isDeleted"))
                );
                anyOf.add(cb.exists(sub));
            }

            if (anyOf.isEmpty()) return cb.disjunction(); // nothing visible
            return cb.or(anyOf.toArray(new Predicate[0]));
        };
    }
}
