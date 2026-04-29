package com.qms.module.lms.repository;

import com.qms.module.lms.entity.Assessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AssessmentRepository extends JpaRepository<Assessment, Long> {

    Optional<Assessment> findByProgram_Id(Long programId);

    boolean existsByProgram_Id(Long programId);
}
