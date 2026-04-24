package com.qms.module.user.repository;

import com.qms.module.user.entity.PasswordHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, Long> {

    /**
     * Returns the N most-recent password hashes for the given user,
     * ordered newest first.  N = policy.previousPasswordAttemptTrack.
     */
    @Query("""
            SELECT h FROM PasswordHistory h
            WHERE h.userId = :userId
            ORDER BY h.createdAt DESC
            LIMIT :limit
            """)
    List<PasswordHistory> findRecentByUserId(
            @Param("userId") Long userId,
            @Param("limit")  int  limit);

    /**
     * Deletes old history rows beyond the N most recent so the table
     * does not grow unboundedly.
     */
    @Modifying
    @Query(value = """
            DELETE FROM password_histories
            WHERE user_id = :userId
              AND id NOT IN (
                  SELECT id FROM password_histories
                  WHERE user_id = :userId
                  ORDER BY created_at DESC
                  LIMIT :keep
              )
            """, nativeQuery = true)
    void pruneOldHistory(@Param("userId") Long userId, @Param("keep") int keep);
}
