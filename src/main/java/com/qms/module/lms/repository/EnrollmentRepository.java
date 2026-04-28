package com.qms.module.lms.repository;

import com.qms.module.lms.entity.Enrollment;
import com.qms.module.lms.enums.EnrollmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long>, JpaSpecificationExecutor<Enrollment> {

    Optional<Enrollment> findByIdAndIsDeletedFalse(Long id);

    Optional<Enrollment> findByUserIdAndProgram_IdAndIsDeletedFalse(Long userId, Long programId);

    List<Enrollment> findByUserIdAndIsDeletedFalse(Long userId);

    @Query("""
            SELECT e FROM Enrollment e
            WHERE e.isDeleted = false
              AND (:userId     IS NULL OR e.userId      = :userId)
              AND (:programId  IS NULL OR e.program.id  = :programId)
              AND (:status     IS NULL OR e.status      = :status)
              AND (:department IS NULL OR e.userDepartment = :department)
              AND (:overdue    IS NULL OR :overdue = false
                   OR (e.dueDate < CURRENT_DATE AND e.status NOT IN ('COMPLETED','WAIVED','CANCELLED')))
            """)
    Page<Enrollment> search(@Param("userId")     Long             userId,
                            @Param("programId")  Long             programId,
                            @Param("status")     EnrollmentStatus status,
                            @Param("department") String           department,
                            @Param("overdue")    Boolean          overdue,
                            Pageable pageable);

    // ── Dashboard & compliance queries ────────────────────────

    long countByStatusAndIsDeletedFalse(EnrollmentStatus status);

    long countByProgram_IdAndStatusAndIsDeletedFalse(Long programId, EnrollmentStatus status);

    @Query("""
            SELECT e FROM Enrollment e
            WHERE e.isDeleted = false
              AND e.dueDate < :today
              AND e.status NOT IN ('COMPLETED','WAIVED','CANCELLED')
            """)
    List<Enrollment> findOverdue(@Param("today") LocalDate today);

    @Query("""
            SELECT e FROM Enrollment e
            WHERE e.isDeleted = false
              AND e.dueDate BETWEEN :today AND :warningDate
              AND e.status NOT IN ('COMPLETED','WAIVED','CANCELLED')
            """)
    List<Enrollment> findDueSoon(@Param("today")       LocalDate today,
                                 @Param("warningDate") LocalDate warningDate);

    @Query("""
            SELECT COUNT(e) FROM Enrollment e
            WHERE e.isDeleted = false
              AND e.userId = :userId
              AND e.status IN ('ENROLLED','IN_PROGRESS')
            """)
    long countPendingByUser(@Param("userId") Long userId);

    /** Compliance rate for a given program: % of non-cancelled enrollments that are COMPLETED/WAIVED. */
    @Query("""
            SELECT
              COUNT(CASE WHEN e.status IN ('COMPLETED','WAIVED') THEN 1 END) * 100.0 /
              NULLIF(COUNT(CASE WHEN e.status != 'CANCELLED' THEN 1 END), 0)
            FROM Enrollment e
            WHERE e.program.id = :programId AND e.isDeleted = false
            """)
    Double getComplianceRateForProgram(@Param("programId") Long programId);

    // ── Notification queries (per-user) ───────────────────────

    /** Overdue enrollments for a specific user. */
    @Query("""
            SELECT e FROM Enrollment e
            WHERE e.isDeleted = false
              AND e.userId = :userId
              AND e.dueDate < :today
              AND e.status NOT IN ('COMPLETED','WAIVED','CANCELLED')
            ORDER BY e.dueDate ASC
            """)
    List<Enrollment> findOverdueForUser(@Param("userId") Long userId,
                                        @Param("today")  LocalDate today);

    /** Enrollments due within the warning window for a specific user. */
    @Query("""
            SELECT e FROM Enrollment e
            WHERE e.isDeleted = false
              AND e.userId = :userId
              AND e.dueDate BETWEEN :today AND :warningDate
              AND e.status NOT IN ('COMPLETED','WAIVED','CANCELLED')
            ORDER BY e.dueDate ASC
            """)
    List<Enrollment> findDueSoonForUser(@Param("userId")      Long      userId,
                                        @Param("today")       LocalDate today,
                                        @Param("warningDate") LocalDate warningDate);
}
