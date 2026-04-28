package com.qms.module.lms.repository;

import com.qms.module.lms.entity.TrainingNeedIdentification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TrainingNeedIdentificationRepository extends JpaRepository<TrainingNeedIdentification, Long> {

    Optional<TrainingNeedIdentification> findByEnrollmentId(Long enrollmentId);

    List<TrainingNeedIdentification> findByUserId(Long userId);

    List<TrainingNeedIdentification> findByDepartment(String department);
}
