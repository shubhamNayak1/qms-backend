package com.qms.module.qms.changecontrol.repository;

import com.qms.common.enums.Priority;
import com.qms.common.enums.QmsStatus;
import com.qms.module.qms.changecontrol.entity.ChangeControl;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class ChangeControlSpecification {

    private ChangeControlSpecification() {}

    public static Specification<ChangeControl> filter(QmsStatus status, Priority priority,
                                                       String changeType, String riskLevel,
                                                       Long assignedTo, String department,
                                                       String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (priority != null) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }
            if (changeType != null && !changeType.isBlank()) {
                predicates.add(cb.equal(root.get("changeType"), changeType));
            }
            if (riskLevel != null && !riskLevel.isBlank()) {
                predicates.add(cb.equal(root.get("riskLevel"), riskLevel));
            }
            if (assignedTo != null) {
                predicates.add(cb.equal(root.get("assignedToId"), assignedTo));
            }
            if (department != null && !department.isBlank()) {
                predicates.add(cb.equal(root.get("department"), department));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(root.get("recordNumber")), pattern)
                ));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
