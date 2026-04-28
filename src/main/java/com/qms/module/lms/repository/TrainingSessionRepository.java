package com.qms.module.lms.repository;

import com.qms.module.lms.entity.TrainingSession;
import com.qms.module.lms.enums.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TrainingSessionRepository extends JpaRepository<TrainingSession, Long> {

    List<TrainingSession> findByProgram_IdOrderBySessionDateAsc(Long programId);

    List<TrainingSession> findByProgram_IdAndStatusOrderBySessionDateAsc(Long programId, SessionStatus status);

    /** Sessions whose attendance window overlaps today (±2 days).
     *  Caller must pass windowStart = today.minusDays(2), windowEnd = today.plusDays(2). */
    @Query("""
            SELECT s FROM TrainingSession s
            WHERE s.program.id = :programId
              AND s.status = 'SCHEDULED'
              AND s.sessionDate >= :windowStart
              AND s.sessionDate <= :windowEnd
            """)
    List<TrainingSession> findActiveSessionsForProgram(@Param("programId")   Long      programId,
                                                        @Param("windowStart") LocalDate windowStart,
                                                        @Param("windowEnd")   LocalDate windowEnd);

    @Query("""
            SELECT COUNT(s) FROM TrainingSession s
            WHERE s.program.id = :programId
              AND s.status NOT IN ('CANCELLED')
            """)
    long countActiveSessionsByProgram(@Param("programId") Long programId);
}
