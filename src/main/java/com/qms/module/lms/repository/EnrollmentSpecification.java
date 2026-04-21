package com.qms.module.lms.repository;

import com.qms.module.lms.entity.Enrollment;
import com.qms.module.lms.enums.EnrollmentStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class EnrollmentSpecification {

    private EnrollmentSpecification() {}

    public static Specification<Enrollment> filter(Long userId, Long programId,
                                                    EnrollmentStatus status, String department,
                                                    Boolean overdue) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (programId != null) {
                predicates.add(cb.equal(root.get("program").get("id"), programId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (department != null && !department.isBlank()) {
                predicates.add(cb.equal(root.get("userDepartment"), department));
            }
            if (Boolean.TRUE.equals(overdue)) {
                predicates.add(cb.and(
                        cb.lessThan(root.get("dueDate"), LocalDate.now()),
                        cb.not(root.get("status").in(
                                EnrollmentStatus.COMPLETED,
                                EnrollmentStatus.WAIVED,
                                EnrollmentStatus.CANCELLED))
                ));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
