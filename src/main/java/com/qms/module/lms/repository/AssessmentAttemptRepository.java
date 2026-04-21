package com.qms.module.lms.repository;

import com.qms.module.lms.entity.AssessmentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssessmentAttemptRepository extends JpaRepository<AssessmentAttempt, Long> {

    List<AssessmentAttempt> findByEnrollment_IdOrderByAttemptNumberDesc(Long enrollmentId);

    Optional<AssessmentAttempt> findTopByEnrollment_IdOrderByAttemptNumberDesc(Long enrollmentId);

    long countByEnrollment_Id(Long enrollmentId);

    @Query("""
            SELECT a FROM AssessmentAttempt a
            WHERE a.enrollment.id = :enrollmentId
              AND a.status = 'IN_PROGRESS'
            """)
    Optional<AssessmentAttempt> findActiveAttempt(@Param("enrollmentId") Long enrollmentId);

    @Query("""
            SELECT a FROM AssessmentAttempt a
            WHERE a.status = 'PENDING_REVIEW'
            ORDER BY a.submittedAt ASC
            """)
    List<AssessmentAttempt> findPendingManualReview();
}
