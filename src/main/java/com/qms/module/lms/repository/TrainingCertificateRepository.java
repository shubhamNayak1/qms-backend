package com.qms.module.lms.repository;

import com.qms.module.lms.entity.TrainingCertificate;
import com.qms.module.lms.enums.CertificateStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TrainingCertificateRepository extends JpaRepository<TrainingCertificate, Long> {

    Optional<TrainingCertificate> findByCertificateNumber(String certificateNumber);

    Page<TrainingCertificate> findAllByOrderByIssuedDateDesc(Pageable pageable);

    Page<TrainingCertificate> findByStatusOrderByIssuedDateDesc(CertificateStatus status, Pageable pageable);

    Optional<TrainingCertificate> findByEnrollment_Id(Long enrollmentId);

    Page<TrainingCertificate> findByUserIdOrderByIssuedDateDesc(Long userId, Pageable pageable);

    List<TrainingCertificate> findByUserIdAndProgramIdAndStatus(Long userId, Long programId,
                                                                  CertificateStatus status);

    @Query("""
            SELECT c FROM TrainingCertificate c
            WHERE c.status = 'ACTIVE'
              AND c.expiryDate BETWEEN :today AND :warningDate
            ORDER BY c.expiryDate ASC
            """)
    List<TrainingCertificate> findExpiringSoon(@Param("today")       LocalDate today,
                                               @Param("warningDate") LocalDate warningDate);

    @Query("""
            SELECT c FROM TrainingCertificate c
            WHERE c.status = 'ACTIVE'
              AND c.expiryDate < :today
            """)
    List<TrainingCertificate> findLapsed(@Param("today") LocalDate today);

    // ── Notification queries (per-user) ───────────────────────

    /** ACTIVE certificates for a user expiring within the warning window. */
    @Query("""
            SELECT c FROM TrainingCertificate c
            WHERE c.status = 'ACTIVE'
              AND c.userId = :userId
              AND c.expiryDate BETWEEN :today AND :warningDate
            ORDER BY c.expiryDate ASC
            """)
    List<TrainingCertificate> findExpiringSoonForUser(@Param("userId")      Long      userId,
                                                      @Param("today")       LocalDate today,
                                                      @Param("warningDate") LocalDate warningDate);

    /** ACTIVE certificates for a user that have already lapsed (expiryDate < today). */
    @Query("""
            SELECT c FROM TrainingCertificate c
            WHERE c.status = 'ACTIVE'
              AND c.userId = :userId
              AND c.expiryDate < :today
            """)
    List<TrainingCertificate> findExpiredForUser(@Param("userId") Long      userId,
                                                 @Param("today")  LocalDate today);
}
