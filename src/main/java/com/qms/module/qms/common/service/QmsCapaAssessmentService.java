package com.qms.module.qms.common.service;

import com.qms.common.enums.AuditAction;
import com.qms.common.enums.AuditModule;
import com.qms.common.enums.QmsStatus;
import com.qms.common.exception.AppException;
import com.qms.module.audit.annotation.Audited;
import com.qms.module.org.service.OrgSecurityService;
import com.qms.module.qms.capa.entity.Capa;
import com.qms.module.qms.capa.repository.CapaRepository;
import com.qms.module.qms.common.dto.request.QmsCapaAssessmentRequest;
import com.qms.module.qms.common.dto.request.QmsCapaAssessmentReviewRequest;
import com.qms.module.qms.common.dto.response.QmsCapaAssessmentResponse;
import com.qms.module.qms.common.entity.QmsCapaAssessment;
import com.qms.module.qms.common.repository.QmsCapaAssessmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages the post-closure CAPA effectiveness-assessment lifecycle.
 *
 * Three operations:
 *   • {@link #seed} — called by CapaService when Head QA closes a CAPA;
 *     creates one row per scheduled cycle and moves the CAPA to
 *     EFFECTIVENESS_PENDING (or stays at CLOSED when assessment_count == 0).
 *   • {@link #submit} — responsible-dept member fills a single cycle's
 *     observed-effectiveness narrative.
 *   • {@link #review} — QA Reviewer accepts or rejects a submitted cycle.
 *
 * The parent CAPA's status is auto-updated as cycles flow through:
 *   any row PENDING       → CAPA at EFFECTIVENESS_PENDING
 *   any row SUBMITTED     → CAPA at EFFECTIVENESS_REVIEW
 *   every row ACCEPTED    → CAPA at EFFECTIVENESS_VERIFIED (terminal)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QmsCapaAssessmentService {

    private final QmsCapaAssessmentRepository assessmentRepository;
    private final CapaRepository              capaRepository;
    private final OrgSecurityService          orgSecurity;

    public List<QmsCapaAssessmentResponse> list(Long capaId) {
        return assessmentRepository
                .findAllByCapaIdAndIsDeletedFalseOrderBySequenceNoAsc(capaId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Seeds the assessment cycle table when Head QA closes a CAPA. Idempotent
     * — if rows already exist for this CAPA, returns the existing list.
     */
    @Audited(action = AuditAction.CREATE, module = AuditModule.CAPA,
             entityType = "QmsCapaAssessment",
             description = "CAPA effectiveness-assessment cycles seeded at closure")
    @Transactional
    public List<QmsCapaAssessmentResponse> seed(Long capaId,
                                                 String frequency,
                                                 Integer count) {
        Capa capa = requireCapa(capaId);

        long existing = assessmentRepository.countByCapaIdAndIsDeletedFalse(capaId);
        if (existing > 0) {
            log.info("CAPA {} already has {} assessment rows; skipping seed.",
                    capa.getRecordNumber(), existing);
            return list(capaId);
        }
        if (count == null || count < 1) {
            // Mark assessment as not required and leave the CAPA at CLOSED.
            capa.setAssessmentSummaryStatus("NOT_REQUIRED");
            capaRepository.save(capa);
            return List.of();
        }

        List<QmsCapaAssessment> rows = new ArrayList<>();
        LocalDate baseDate = capa.getClosedDate() != null
                ? capa.getClosedDate()
                : LocalDate.now();
        int monthGap = monthsForFrequency(frequency);

        for (int i = 1; i <= count; i++) {
            LocalDate due = monthGap > 0 ? baseDate.plusMonths((long) i * monthGap) : null;
            QmsCapaAssessment row = QmsCapaAssessment.builder()
                    .capaId(capaId)
                    .sequenceNo(i)
                    .dueDate(due)
                    .status("PENDING")
                    .build();
            rows.add(assessmentRepository.save(row));
        }

        capa.setAssessmentFrequency(frequency);
        capa.setAssessmentCount(count);
        capa.setAssessmentSummaryStatus("IN_PROGRESS");
        capa.setStatus(QmsStatus.EFFECTIVENESS_PENDING);
        capaRepository.save(capa);

        log.info("Seeded {} effectiveness-assessment rows for CAPA {}",
                rows.size(), capa.getRecordNumber());
        return rows.stream().map(this::toResponse).toList();
    }

    /**
     * Responsible-dept member fills a single cycle row. Pushes the CAPA's
     * status into EFFECTIVENESS_REVIEW (someone needs to review).
     */
    @Audited(action = AuditAction.UPDATE, module = AuditModule.CAPA,
             entityType = "QmsCapaAssessment", entityIdArgIndex = 0,
             description = "Responsible dept submitted a CAPA effectiveness cycle")
    @Transactional
    public QmsCapaAssessmentResponse submit(Long rowId, QmsCapaAssessmentRequest req) {
        QmsCapaAssessment row = requireRow(rowId);
        if (!"PENDING".equalsIgnoreCase(row.getStatus())
            && !"REJECTED".equalsIgnoreCase(row.getStatus())) {
            throw AppException.badRequest("Assessment cycle is in status " + row.getStatus()
                    + " — only PENDING or REJECTED rows can be submitted.");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = (auth != null && auth.isAuthenticated()) ? auth.getName() : "SYSTEM";
        Long userId = orgSecurity.currentUser().map(u -> u.getId()).orElse(null);

        row.setActionObserved(req.getActionObserved());
        row.setEvidenceRef(req.getEvidenceRef());
        row.setIsEffective(req.getIsEffective());
        row.setStatus("SUBMITTED");
        row.setCompletedById(userId);
        row.setCompletedByName(username);
        row.setCompletedAt(LocalDateTime.now());
        row.setReviewStatus(null);   // clear any prior rejection
        row.setReviewComment(null);
        row.setReviewedById(null);
        row.setReviewedByName(null);
        row.setReviewedAt(null);

        QmsCapaAssessment saved = assessmentRepository.save(row);
        promoteCapaStatus(saved.getCapaId());
        return toResponse(saved);
    }

    /**
     * QA Reviewer accepts or rejects a submitted cycle.
     */
    @Audited(action = AuditAction.APPROVE, module = AuditModule.CAPA,
             entityType = "QmsCapaAssessment", entityIdArgIndex = 0,
             description = "QA Reviewer decided a CAPA effectiveness cycle")
    @Transactional
    public QmsCapaAssessmentResponse review(Long rowId, QmsCapaAssessmentReviewRequest req) {
        QmsCapaAssessment row = requireRow(rowId);
        if (!"SUBMITTED".equalsIgnoreCase(row.getStatus())) {
            throw AppException.badRequest("Only SUBMITTED rows can be reviewed; this row is "
                    + row.getStatus());
        }

        String decision = req.getDecision();
        if (decision == null
                || (!decision.equalsIgnoreCase("ACCEPTED")
                 && !decision.equalsIgnoreCase("REJECTED"))) {
            throw AppException.badRequest("decision must be ACCEPTED or REJECTED");
        }
        if (req.getComment() == null || req.getComment().isBlank()) {
            throw AppException.badRequest("Review comment is required.");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = (auth != null && auth.isAuthenticated()) ? auth.getName() : "SYSTEM";
        Long userId = orgSecurity.currentUser().map(u -> u.getId()).orElse(null);

        row.setReviewStatus(decision.toUpperCase());
        row.setReviewComment(req.getComment());
        row.setReviewedById(userId);
        row.setReviewedByName(username);
        row.setReviewedAt(LocalDateTime.now());
        row.setStatus(decision.equalsIgnoreCase("ACCEPTED") ? "ACCEPTED" : "REJECTED");

        QmsCapaAssessment saved = assessmentRepository.save(row);
        promoteCapaStatus(saved.getCapaId());
        return toResponse(saved);
    }

    // ── Helpers ──────────────────────────────────────────────

    /**
     * Re-evaluates the CAPA's overall status based on its assessment rows:
     *   • Every row ACCEPTED  → EFFECTIVENESS_VERIFIED + COMPLETE
     *   • Any row SUBMITTED   → EFFECTIVENESS_REVIEW
     *   • Otherwise (some PENDING/REJECTED) → EFFECTIVENESS_PENDING
     */
    private void promoteCapaStatus(Long capaId) {
        Capa capa = requireCapa(capaId);

        long total       = assessmentRepository.countByCapaIdAndIsDeletedFalse(capaId);
        if (total == 0) return;
        long pending     = assessmentRepository.countByCapaIdAndStatusAndIsDeletedFalse(
                                capaId, "PENDING");
        long submitted   = assessmentRepository.countByCapaIdAndStatusAndIsDeletedFalse(
                                capaId, "SUBMITTED");
        long rejected    = assessmentRepository.countByCapaIdAndStatusAndIsDeletedFalse(
                                capaId, "REJECTED");
        long unaccepted  = assessmentRepository
                                .countByCapaIdAndReviewStatusNotAndIsDeletedFalse(
                                        capaId, "ACCEPTED");

        QmsStatus newStatus;
        String summary;

        if (unaccepted == 0) {
            newStatus = QmsStatus.EFFECTIVENESS_VERIFIED;
            summary   = "COMPLETE";
        } else if (submitted > 0) {
            newStatus = QmsStatus.EFFECTIVENESS_REVIEW;
            summary   = "IN_PROGRESS";
        } else {
            // pending or rejected outstanding
            newStatus = QmsStatus.EFFECTIVENESS_PENDING;
            summary   = "IN_PROGRESS";
        }

        capa.setStatus(newStatus);
        capa.setAssessmentSummaryStatus(summary);
        capaRepository.save(capa);

        log.debug("CAPA {} → {} (pending={}, submitted={}, rejected={}, unaccepted={})",
                capa.getRecordNumber(), newStatus, pending, submitted, rejected, unaccepted);
    }

    private static int monthsForFrequency(String f) {
        if (f == null) return 0;
        return switch (f.toUpperCase()) {
            case "MONTHLY"     -> 1;
            case "QUARTERLY"   -> 3;
            case "SEMI_ANNUAL" -> 6;
            case "ANNUAL"      -> 12;
            default            -> 0;
        };
    }

    private Capa requireCapa(Long id) {
        return capaRepository.findById(id)
                .orElseThrow(() -> AppException.notFound("CAPA", id));
    }

    private QmsCapaAssessment requireRow(Long id) {
        return assessmentRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> AppException.notFound("CAPA Assessment row", id));
    }

    private QmsCapaAssessmentResponse toResponse(QmsCapaAssessment r) {
        return QmsCapaAssessmentResponse.builder()
                .id(r.getId())
                .capaId(r.getCapaId())
                .sequenceNo(r.getSequenceNo())
                .dueDate(r.getDueDate())
                .status(r.getStatus())
                .actionObserved(r.getActionObserved())
                .evidenceRef(r.getEvidenceRef())
                .isEffective(r.getIsEffective())
                .completedById(r.getCompletedById())
                .completedByName(r.getCompletedByName())
                .completedAt(r.getCompletedAt())
                .reviewStatus(r.getReviewStatus())
                .reviewComment(r.getReviewComment())
                .reviewedById(r.getReviewedById())
                .reviewedByName(r.getReviewedByName())
                .reviewedAt(r.getReviewedAt())
                .build();
    }
}
