package com.qms.module.lms.repository;

import com.qms.module.lms.entity.ComplianceReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ComplianceReviewRepository extends JpaRepository<ComplianceReview, Long> {

    Optional<ComplianceReview> findBySubmission_Id(Long submissionId);
}
