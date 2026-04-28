package com.qms.module.lms.repository;

import com.qms.module.lms.entity.ComplianceSubmission;
import com.qms.module.lms.enums.ComplianceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ComplianceSubmissionRepository extends JpaRepository<ComplianceSubmission, Long> {

    Optional<ComplianceSubmission> findByEnrollment_Id(Long enrollmentId);

    List<ComplianceSubmission> findByStatus(ComplianceStatus status);

    boolean existsByEnrollment_Id(Long enrollmentId);
}
