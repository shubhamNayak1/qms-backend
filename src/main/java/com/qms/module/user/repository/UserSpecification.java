package com.qms.module.user.repository;

import com.qms.module.user.entity.User;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class UserSpecification {

    private UserSpecification() {}

    /**
     * Builds a single Specification from all optional filter params.
     * Null / blank params are silently ignored — no predicate is added for them.
     * This avoids the JPQL "IS NULL" pattern that fails on PostgreSQL when
     * Hibernate cannot infer the type of a null-bound parameter.
     */
    public static Specification<User> filter(String search, String department, Boolean isActive) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Always exclude soft-deleted rows
            predicates.add(cb.isFalse(root.get("isDeleted")));

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("username")),   pattern),
                    cb.like(cb.lower(root.get("email")),      pattern),
                    cb.like(cb.lower(root.get("firstName")),  pattern),
                    cb.like(cb.lower(root.get("lastName")),   pattern),
                    cb.like(cb.lower(root.get("employeeId")), pattern)
                ));
            }

            if (department != null && !department.isBlank()) {
                predicates.add(cb.equal(root.get("department"), department));
            }

            if (isActive != null) {
                predicates.add(cb.equal(root.get("isActive"), isActive));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
