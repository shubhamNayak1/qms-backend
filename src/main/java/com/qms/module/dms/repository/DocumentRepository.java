package com.qms.module.dms.repository;

import com.qms.module.dms.entity.Document;
import com.qms.module.dms.enums.DocumentCategory;
import com.qms.module.dms.enums.DocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long>, JpaSpecificationExecutor<Document> {

    // ── Lookup ────────────────────────────────────────────────

    Optional<Document> findByIdAndIsDeletedFalse(Long id);

    /** Latest EFFECTIVE version of a document by its doc number. */
    @Query("""
            SELECT d FROM Document d
            WHERE d.docNumber = :docNumber AND d.status = 'EFFECTIVE'
              AND d.isDeleted = false
            ORDER BY d.majorVersion DESC, d.minorVersion DESC
            """)
    Optional<Document> findCurrentEffectiveByDocNumber(@Param("docNumber") String docNumber);

    /** All versions of a document, most recent first. */
    @Query("""
            SELECT d FROM Document d
            WHERE d.docNumber = :docNumber AND d.isDeleted = false
            ORDER BY d.majorVersion DESC, d.minorVersion DESC
            """)
    List<Document> findAllVersionsByDocNumber(@Param("docNumber") String docNumber);

    /** A specific version by doc number + version string. */
    Optional<Document> findByDocNumberAndVersionAndIsDeletedFalse(String docNumber, String version);

    // ── Search ────────────────────────────────────────────────

    /**
     * @param search Pre-built lower-cased wildcard pattern, e.g. {@code %sop%},
     *               or {@code null} to skip text filtering.
     *               Built by the service layer to avoid Hibernate 6 binding a null
     *               String as {@code bytea} ("function lower(bytea) does not exist").
     */
    @Query("""
            SELECT d FROM Document d
            WHERE d.isDeleted = false
              AND (:status     IS NULL OR d.status     = :status)
              AND (:category   IS NULL OR d.category   = :category)
              AND (:department IS NULL OR d.department = :department)
              AND (:ownerId    IS NULL OR d.ownerId    = :ownerId)
              AND (:search     IS NULL
                   OR LOWER(d.title)     LIKE :search
                   OR LOWER(d.docNumber) LIKE :search
                   OR LOWER(d.tags)      LIKE :search)
            """)
    Page<Document> search(@Param("status")     DocumentStatus   status,
                          @Param("category")   DocumentCategory category,
                          @Param("department") String           department,
                          @Param("ownerId")    Long             ownerId,
                          @Param("search")     String           search,
                          Pageable pageable);

    // ── Dashboard counters ────────────────────────────────────

    long countByStatusAndIsDeletedFalse(DocumentStatus status);

    // ── Expiry / review tracking ──────────────────────────────

    @Query("""
            SELECT d FROM Document d
            WHERE d.isDeleted = false
              AND d.status = 'EFFECTIVE'
              AND d.expiryDate BETWEEN :today AND :warningDate
            ORDER BY d.expiryDate ASC
            """)
    List<Document> findExpiringSoon(@Param("today")       LocalDate today,
                                    @Param("warningDate") LocalDate warningDate);

    @Query("""
            SELECT d FROM Document d
            WHERE d.isDeleted = false
              AND d.status = 'EFFECTIVE'
              AND d.expiryDate < :today
            """)
    List<Document> findExpired(@Param("today") LocalDate today);

    @Query("""
            SELECT d FROM Document d
            WHERE d.isDeleted = false
              AND d.status = 'EFFECTIVE'
              AND d.reviewDate BETWEEN :today AND :warningDate
            ORDER BY d.reviewDate ASC
            """)
    List<Document> findDueForReview(@Param("today")       LocalDate today,
                                    @Param("warningDate") LocalDate warningDate);

    // ── Notification queries (per-owner) ─────────────────────

    /** EFFECTIVE documents owned by the user that are expiring soon. */
    @Query("""
            SELECT d FROM Document d
            WHERE d.isDeleted = false
              AND d.status = 'EFFECTIVE'
              AND d.ownerId = :ownerId
              AND d.expiryDate BETWEEN :today AND :warningDate
            ORDER BY d.expiryDate ASC
            """)
    List<Document> findExpiringSoonForOwner(@Param("ownerId")     Long      ownerId,
                                            @Param("today")       LocalDate today,
                                            @Param("warningDate") LocalDate warningDate);

    /** EFFECTIVE documents owned by the user that are due for periodic review. */
    @Query("""
            SELECT d FROM Document d
            WHERE d.isDeleted = false
              AND d.status = 'EFFECTIVE'
              AND d.ownerId = :ownerId
              AND d.reviewDate BETWEEN :today AND :warningDate
            ORDER BY d.reviewDate ASC
            """)
    List<Document> findDueForReviewForOwner(@Param("ownerId")     Long      ownerId,
                                            @Param("today")       LocalDate today,
                                            @Param("warningDate") LocalDate warningDate);

    // ── Atomic download count increment ───────────────────────

    @Modifying
    @Query("UPDATE Document d SET d.downloadCount = d.downloadCount + 1 WHERE d.id = :id")
    void incrementDownloadCount(@Param("id") Long id);

    // ── Doc number uniqueness check ───────────────────────────

    boolean existsByDocNumberAndIsDeletedFalse(String docNumber);

    /**
     * Returns the highest 4-digit sequence already allocated for a given prefix
     * (e.g. "DOC-SOP-202404-"). The caller adds 1 to get the next sequence.
     * The docNumber unique constraint guarantees a retry-on-conflict strategy
     * handles the narrow concurrent-insert window.
     */
    @Query("""
            SELECT COALESCE(MAX(CAST(SUBSTRING(d.docNumber, :offset) AS int)), 0)
            FROM Document d
            WHERE d.docNumber LIKE CONCAT(:prefix, '%')
            """)
    int findMaxSequenceForPrefix(@Param("prefix") String prefix,
                                 @Param("offset") int offset);
}
