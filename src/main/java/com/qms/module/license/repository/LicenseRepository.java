package com.qms.module.license.repository;

import com.qms.module.license.entity.License;
import com.qms.module.license.enums.LicenseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface LicenseRepository extends JpaRepository<License, Long> {

    Optional<License> findByIdAndIsDeletedFalse(Long id);

    Optional<License> findByCodeAndIsDeletedFalse(String code);

    boolean existsByCodeAndIsDeletedFalse(String code);

    /**
     * Returns the active assigned license for a user, if any.
     * "Active" = status ASSIGNED and (no expiry OR expiry > now).
     */
    @Query("""
           SELECT l FROM License l
            WHERE l.assignedToUserId = :userId
              AND l.status = com.qms.module.license.enums.LicenseStatus.ASSIGNED
              AND l.isDeleted = FALSE
              AND (l.expiresAt IS NULL OR l.expiresAt > :now)
           """)
    Optional<License> findActiveLicenseForUser(@Param("userId") Long userId,
                                               @Param("now")    LocalDateTime now);

    Page<License> findAllByStatusAndIsDeletedFalse(LicenseStatus status, Pageable pageable);

    Page<License> findAllByIsDeletedFalse(Pageable pageable);

    long countByStatusAndIsDeletedFalse(LicenseStatus status);
}
