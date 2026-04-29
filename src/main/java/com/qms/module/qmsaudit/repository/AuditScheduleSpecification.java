package com.qms.module.qmsaudit.repository;

import com.qms.module.qmsaudit.entity.AuditSchedule;
import com.qms.module.qmsaudit.enums.AuditScheduleStatus;
import com.qms.module.qmsaudit.enums.AuditType;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

public class AuditScheduleSpecification {

    public static Specification<AuditSchedule> filter(
            AuditType type,
            AuditScheduleStatus status,
            LocalDate from,
            LocalDate to,
            String search) {

        return Specification
                .where(notDeleted())
                .and(hasType(type))
                .and(hasStatus(status))
                .and(scheduledAfter(from))
                .and(scheduledBefore(to))
                .and(titleOrNumberContains(search));
    }

    private static Specification<AuditSchedule> notDeleted() {
        return (r, q, cb) -> cb.isFalse(r.get("isDeleted"));
    }

    private static Specification<AuditSchedule> hasType(AuditType type) {
        return type == null ? null : (r, q, cb) -> cb.equal(r.get("auditType"), type);
    }

    private static Specification<AuditSchedule> hasStatus(AuditScheduleStatus status) {
        return status == null ? null : (r, q, cb) -> cb.equal(r.get("status"), status);
    }

    private static Specification<AuditSchedule> scheduledAfter(LocalDate from) {
        return from == null ? null
                : (r, q, cb) -> cb.greaterThanOrEqualTo(r.get("scheduledDate"), from);
    }

    private static Specification<AuditSchedule> scheduledBefore(LocalDate to) {
        return to == null ? null
                : (r, q, cb) -> cb.lessThanOrEqualTo(r.get("scheduledDate"), to);
    }

    private static Specification<AuditSchedule> titleOrNumberContains(String search) {
        if (search == null || search.isBlank()) return null;
        String like = "%" + search.toLowerCase() + "%";
        return (r, q, cb) -> cb.or(
                cb.like(cb.lower(r.get("title")), like),
                cb.like(cb.lower(r.get("auditNumber")), like),
                cb.like(cb.lower(r.get("leadAuditorName")), like)
        );
    }
}
