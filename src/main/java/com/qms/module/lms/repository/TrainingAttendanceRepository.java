package com.qms.module.lms.repository;

import com.qms.module.lms.entity.TrainingAttendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TrainingAttendanceRepository extends JpaRepository<TrainingAttendance, Long> {

    List<TrainingAttendance> findBySession_Id(Long sessionId);

    List<TrainingAttendance> findByEnrollmentId(Long enrollmentId);

    Optional<TrainingAttendance> findBySession_IdAndEnrollmentId(Long sessionId, Long enrollmentId);

    Optional<TrainingAttendance> findBySession_IdAndUserId(Long sessionId, Long userId);

    @Query("""
            SELECT COUNT(a) FROM TrainingAttendance a
            WHERE a.session.id = :sessionId AND a.isPresent = true
            """)
    long countPresentBySession(@Param("sessionId") Long sessionId);
}
