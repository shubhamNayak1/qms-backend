package com.qms.module.dms.repository;

import com.qms.module.dms.entity.DocumentApproval;
import com.qms.module.dms.enums.DocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentApprovalRepository extends JpaRepository<DocumentApproval, Long> {

    List<DocumentApproval> findByDocument_IdOrderByCreatedAtAsc(Long documentId);

    @Query("""
            SELECT a FROM DocumentApproval a
            WHERE a.document.id = :documentId
              AND a.reviewCycle = :cycle
            ORDER BY a.createdAt ASC
            """)
    List<DocumentApproval> findByCycleAndDocumentId(@Param("documentId") Long documentId,
                                                     @Param("cycle")      int   cycle);

    /** Find any pending (UNDER_REVIEW) approval actions for a given approver. */
    @Query("""
            SELECT a FROM DocumentApproval a
            WHERE a.approverId = :approverId
              AND a.decision = 'UNDER_REVIEW'
            ORDER BY a.createdAt DESC
            """)
    List<DocumentApproval> findPendingByApprover(@Param("approverId") Long approverId);

    long countByDocument_IdAndDecision(Long documentId, DocumentStatus decision);
}
