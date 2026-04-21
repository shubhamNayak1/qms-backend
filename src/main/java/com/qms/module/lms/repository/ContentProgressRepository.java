package com.qms.module.lms.repository;

import com.qms.module.lms.entity.ContentProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContentProgressRepository extends JpaRepository<ContentProgress, Long> {

    List<ContentProgress> findByEnrollment_IdOrderByContent_DisplayOrderAsc(Long enrollmentId);

    Optional<ContentProgress> findByEnrollment_IdAndContent_Id(Long enrollmentId, Long contentId);

    long countByEnrollment_IdAndIsCompletedTrue(Long enrollmentId);

    @Query("""
            SELECT cp FROM ContentProgress cp
            WHERE cp.enrollment.id = :enrollmentId
              AND cp.content.isRequired = true
              AND cp.isCompleted = false
            """)
    List<ContentProgress> findIncompleteRequiredByEnrollment(@Param("enrollmentId") Long enrollmentId);
}
