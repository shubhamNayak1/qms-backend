package com.qms.module.qms.common.repository;

import com.qms.module.qms.common.entity.QmsCapaAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QmsCapaAssessmentRepository
        extends JpaRepository<QmsCapaAssessment, Long> {

    List<QmsCapaAssessment> findAllByCapaIdAndIsDeletedFalseOrderBySequenceNoAsc(Long capaId);

    Optional<QmsCapaAssessment> findByIdAndIsDeletedFalse(Long id);

    long countByCapaIdAndIsDeletedFalse(Long capaId);

    /**
     * How many rows have a final state OTHER than ACCEPTED. Used by the
     * service to decide whether the CAPA can move to EFFECTIVENESS_VERIFIED.
     */
    long countByCapaIdAndReviewStatusNotAndIsDeletedFalse(Long capaId, String reviewStatus);

    /** Rows still waiting for the responsible dept to fill. */
    long countByCapaIdAndStatusAndIsDeletedFalse(Long capaId, String status);
}
