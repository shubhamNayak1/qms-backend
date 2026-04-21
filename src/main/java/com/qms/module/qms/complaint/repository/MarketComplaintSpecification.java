package com.qms.module.qms.complaint.repository;

import com.qms.common.enums.Priority;
import com.qms.common.enums.QmsStatus;
import com.qms.module.qms.complaint.entity.MarketComplaint;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class MarketComplaintSpecification {

    private MarketComplaintSpecification() {}

    public static Specification<MarketComplaint> filter(QmsStatus status, Priority priority,
                                                         String complaintCategory, Long assignedTo,
                                                         Boolean reportableOnly, String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (priority != null) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }
            if (complaintCategory != null && !complaintCategory.isBlank()) {
                predicates.add(cb.equal(root.get("complaintCategory"), complaintCategory));
            }
            if (assignedTo != null) {
                predicates.add(cb.equal(root.get("assignedToId"), assignedTo));
            }
            if (Boolean.TRUE.equals(reportableOnly)) {
                predicates.add(cb.isTrue(root.get("reportableToAuthority")));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(root.get("recordNumber")), pattern),
                        cb.like(cb.lower(root.get("customerName")), pattern),
                        cb.like(cb.lower(root.get("productName")), pattern)
                ));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
