package com.qms.module.user.repository;

import com.qms.module.user.entity.PasswordPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PasswordPolicyRepository extends JpaRepository<PasswordPolicy, Long> {

    /**
     * Returns the currently active policy — the non-deleted row with the
     * most recent effectiveDate that is on or before today.
     */
    @Query("""
            SELECT p FROM PasswordPolicy p
            WHERE p.isDeleted = false
              AND p.effectiveDate <= :today
            ORDER BY p.effectiveDate DESC
            LIMIT 1
            """)
    Optional<PasswordPolicy> findActivePolicy(@Param("today") LocalDate today);

    /** All non-deleted policies ordered newest first (for history view). */
    @Query("""
            SELECT p FROM PasswordPolicy p
            WHERE p.isDeleted = false
            ORDER BY p.effectiveDate DESC
            """)
    List<PasswordPolicy> findAllActive();

    /** Check for date conflicts — two policies on the same effective date. */
    boolean existsByEffectiveDateAndIsDeletedFalse(LocalDate effectiveDate);

    boolean existsByEffectiveDateAndIsDeletedFalseAndIdNot(LocalDate effectiveDate, Long id);
}
