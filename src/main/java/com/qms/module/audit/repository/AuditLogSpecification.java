package com.qms.module.audit.repository;

import com.qms.common.enums.AuditAction;
import com.qms.common.enums.AuditModule;
import com.qms.common.enums.AuditOutcome;
import com.qms.module.audit.entity.AuditLog;
import com.qms.module.user.entity.User;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class AuditLogSpecification {

    private AuditLogSpecification() {}

    public static Specification<AuditLog> filter(Long userId, String username,
                                                  AuditAction action, AuditModule module,
                                                  String entityType, Long entityId,
                                                  AuditOutcome outcome, String ipAddress,
                                                  LocalDateTime from, LocalDateTime to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Hide actions performed by system/bootstrap users (SUPER_ADMIN).
            // audit_logs has no FK to users, so we subquery by user_id. Rows
            // without a user_id (anonymous events like LOGIN_FAILED attempts)
            // stay visible.
            Subquery<Long> systemUserIds = query.subquery(Long.class);
            Root<User> systemUserRoot = systemUserIds.from(User.class);
            systemUserIds.select(systemUserRoot.get("id"))
                         .where(cb.isTrue(systemUserRoot.get("isSystem")));
            predicates.add(cb.or(
                root.get("userId").isNull(),
                cb.not(root.get("userId").in(systemUserIds))
            ));

            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (username != null && !username.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("username")),
                        "%" + username.toLowerCase() + "%"));
            }
            if (action != null) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (module != null) {
                predicates.add(cb.equal(root.get("module"), module));
            }
            if (entityType != null && !entityType.isBlank()) {
                predicates.add(cb.equal(root.get("entityType"), entityType));
            }
            if (entityId != null) {
                predicates.add(cb.equal(root.get("entityId"), entityId));
            }
            if (outcome != null) {
                predicates.add(cb.equal(root.get("outcome"), outcome));
            }
            if (ipAddress != null && !ipAddress.isBlank()) {
                predicates.add(cb.equal(root.get("ipAddress"), ipAddress));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
