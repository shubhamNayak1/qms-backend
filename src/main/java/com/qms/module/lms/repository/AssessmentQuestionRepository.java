package com.qms.module.lms.repository;

import com.qms.module.lms.entity.AssessmentQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssessmentQuestionRepository extends JpaRepository<AssessmentQuestion, Long> {

    List<AssessmentQuestion> findByAssessment_IdOrderByDisplayOrderAsc(Long assessmentId);

    Optional<AssessmentQuestion> findByIdAndAssessment_Id(Long id, Long assessmentId);

    long countByAssessment_Id(Long assessmentId);

    void deleteByAssessment_Id(Long assessmentId);
}
