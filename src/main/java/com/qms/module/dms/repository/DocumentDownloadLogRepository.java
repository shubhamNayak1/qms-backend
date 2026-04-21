package com.qms.module.dms.repository;

import com.qms.module.dms.entity.DocumentDownloadLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentDownloadLogRepository extends JpaRepository<DocumentDownloadLog, Long> {

    Page<DocumentDownloadLog> findByDocumentIdOrderByDownloadedAtDesc(Long documentId, Pageable pageable);

    Page<DocumentDownloadLog> findByUserIdOrderByDownloadedAtDesc(Long userId, Pageable pageable);

    @Query("""
            SELECT d FROM DocumentDownloadLog d
            WHERE d.documentId = :documentId AND d.userId = :userId
            ORDER BY d.downloadedAt DESC
            """)
    List<DocumentDownloadLog> findByDocumentAndUser(@Param("documentId") Long documentId,
                                                     @Param("userId") Long userId);

    Optional<DocumentDownloadLog> findByIdAndUserId(Long id, Long userId);

    long countByDocumentIdAndAcknowledgedTrue(Long documentId);

    @Query("""
            SELECT d FROM DocumentDownloadLog d
            WHERE d.documentId = :documentId
              AND d.acknowledged = false
              AND d.downloadedAt < :before
            """)
    List<DocumentDownloadLog> findUnacknowledgedBefore(@Param("documentId") Long documentId,
                                                        @Param("before") LocalDateTime before);
}
