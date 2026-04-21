package com.qms.module.user.repository;

import com.qms.module.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    // ─── Lookup ───────────────────────────────────────────────

    Optional<User> findByUsernameAndIsDeletedFalse(String username);

    Optional<User> findByEmailAndIsDeletedFalse(String email);

    Optional<User> findByIdAndIsDeletedFalse(Long id);

    /** Used by CustomUserDetailsService — accepts username OR email */
    @Query("""
            SELECT u FROM User u
            WHERE u.isDeleted = false
              AND (u.username = :usernameOrEmail OR u.email = :usernameOrEmail)
            """)
    Optional<User> findByUsernameOrEmail(@Param("usernameOrEmail") String usernameOrEmail);

    Optional<User> findByPasswordResetTokenAndIsDeletedFalse(String token);

    // ─── Existence checks ─────────────────────────────────────

    boolean existsByUsernameAndIsDeletedFalse(String username);

    boolean existsByEmailAndIsDeletedFalse(String email);

    boolean existsByEmployeeIdAndIsDeletedFalse(String employeeId);

    // ─── Paginated search ─────────────────────────────────────
    // Filtering is handled via JpaSpecificationExecutor (see UserSpecification).
    // The old JPQL searchUsers query used "IS NULL" parameter checks which fail
    // on PostgreSQL when Hibernate cannot infer the type of a null-bound parameter.

    // ─── Mutations ────────────────────────────────────────────

    @Modifying
    @Query("UPDATE User u SET u.lastLoginAt = :at WHERE u.id = :id")
    void updateLastLoginAt(@Param("id") Long id, @Param("at") LocalDateTime at);

    @Modifying
    @Query("UPDATE User u SET u.failedLoginAttempts = :count WHERE u.id = :id")
    void updateFailedAttempts(@Param("id") Long id, @Param("count") int count);

    @Modifying
    @Query("UPDATE User u SET u.failedLoginAttempts = 0, u.lockedUntil = NULL WHERE u.id = :id")
    void resetLockout(@Param("id") Long id);

    @Modifying
    @Query("UPDATE User u SET u.isActive = :active WHERE u.id = :id")
    void updateActiveStatus(@Param("id") Long id, @Param("active") boolean active);

    @Modifying
    @Query("UPDATE User u SET u.refreshTokenHash = :hash WHERE u.id = :id")
    void updateRefreshTokenHash(@Param("id") Long id, @Param("hash") String hash);

    @Modifying
    @Query("UPDATE User u SET u.passwordHash = :hash, u.passwordChangedAt = :changedAt WHERE u.id = :id")
    void updatePassword(@Param("id") Long id,
                        @Param("hash") String hash,
                        @Param("changedAt") LocalDateTime changedAt);
}
