package com.qms.module.audit.repository;

import com.qms.common.enums.AuditAction;
import com.qms.common.enums.AuditModule;
import com.qms.common.enums.AuditOutcome;
import com.qms.module.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    // ─── Single-field lookups ─────────────────────────────

    List<AuditLog> findByUserIdOrderByTimestampDesc(Long userId);

    List<AuditLog> findBySessionIdOrderByTimestampAsc(String sessionId);

    List<AuditLog> findByCorrelationIdOrderByTimestampAsc(String correlationId);

    List<AuditLog> findByEntityTypeAndEntityId(String entityType, Long entityId);

    // ─── Aggregations for dashboard ───────────────────────

    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.action = :action AND a.timestamp >= :since")
    long countByActionSince(@Param("action") AuditAction action,
                            @Param("since")  LocalDateTime since);

    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.outcome = :outcome AND a.timestamp >= :since")
    long countByOutcomeSince(@Param("outcome") AuditOutcome outcome,
                             @Param("since")   LocalDateTime since);

    @Query("""
            SELECT a.module, COUNT(a)
            FROM AuditLog a
            WHERE a.timestamp >= :since
            GROUP BY a.module
            ORDER BY COUNT(a) DESC
            """)
    List<Object[]> countByModuleSince(@Param("since") LocalDateTime since);

    @Query("""
            SELECT a.action, COUNT(a)
            FROM AuditLog a
            WHERE a.userId = :userId AND a.timestamp >= :since
            GROUP BY a.action
            """)
    List<Object[]> countByActionForUser(@Param("userId") Long userId,
                                        @Param("since")  LocalDateTime since);

    /** Most recent login events — useful for security dashboard. */
    @Query("""
            SELECT a FROM AuditLog a
            WHERE a.action = 'LOGIN' AND a.userId = :userId
            ORDER BY a.timestamp DESC
            """)
    List<AuditLog> findRecentLogins(@Param("userId") Long userId, Pageable pageable);

    /** All failed login attempts since a given time — used for brute-force detection. */
    @Query("""
            SELECT a FROM AuditLog a
            WHERE a.action = 'LOGIN_FAILED'
              AND a.ipAddress = :ip
              AND a.timestamp >= :since
            """)
    List<AuditLog> findFailedLoginsByIp(@Param("ip")    String ip,
                                        @Param("since") LocalDateTime since);

    // ─── Retention / Archival ────────────────────────────

    /** Fetch IDs of logs older than retention threshold — batch for archival. */
    @Query("SELECT a.id FROM AuditLog a WHERE a.timestamp < :before")
    List<Long> findIdsBefore(@Param("before") LocalDateTime before, Pageable pageable);

    @Modifying
    @Query("DELETE FROM AuditLog a WHERE a.id IN :ids")
    void deleteByIds(@Param("ids") List<Long> ids);
}
