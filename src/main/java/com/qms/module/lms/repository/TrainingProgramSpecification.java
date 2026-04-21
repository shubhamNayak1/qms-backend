package com.qms.module.lms.repository;

import com.qms.module.lms.entity.TrainingProgram;
import com.qms.module.lms.enums.ProgramStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class TrainingProgramSpecification {

    private TrainingProgramSpecification() {}

    public static Specification<TrainingProgram> filter(ProgramStatus status, String category,
                                                         String department, Boolean mandatory,
                                                         String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (department != null && !department.isBlank()) {
                predicates.add(cb.equal(root.get("department"), department));
            }
            if (mandatory != null) {
                predicates.add(cb.equal(root.get("isMandatory"), mandatory));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(root.get("code")), pattern),
                        cb.like(cb.lower(root.get("tags")), pattern)
                ));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
