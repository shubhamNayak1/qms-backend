package com.qms.module.qms.common.service;

import com.qms.common.enums.QmsRecordType;
import com.qms.common.exception.AppException;
import com.qms.module.org.service.OrgSecurityService;
import com.qms.module.qms.common.dto.request.TargetDateExtensionDecision;
import com.qms.module.qms.common.dto.request.TargetDateExtensionRequest;
import com.qms.module.qms.common.dto.response.TargetDateExtensionResponse;
import com.qms.module.qms.common.entity.QmsRecord;
import com.qms.module.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Lightweight inline workflow for target-date extensions:
 *
 *   Initiator        → request()  : status = PENDING
 *   HOD / QA Reviewer → decide()   : status = APPROVED | REJECTED
 *
 * On APPROVED, the record's targetCompletionDate is bumped to the extension
 * date so reports / overdue checks reflect the new deadline.
 *
 * The decision and reason are appended to the record's comments column so
 * they appear on the printed Change Control Form ("Justification — Change
 * Control Exceeds Target Date").
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TargetDateExtensionService {

    private static final String STATUS_PENDING  = "PENDING";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";

    private final QmsRecordLookupService recordLookup;
    private final OrgSecurityService     orgSecurity;

    public TargetDateExtensionResponse get(QmsRecordType recordType, Long recordId) {
        QmsRecord r = recordLookup.findByTypeAndId(recordType, recordId);
        return toResponse(r, null);
    }

    @Transactional
    public TargetDateExtensionResponse request(QmsRecordType recordType, Long recordId,
                                                TargetDateExtensionRequest req) {
        QmsRecord r = recordLookup.findByTypeAndId(recordType, recordId);

        if (r.isTerminal()) {
            throw AppException.badRequest(
                    "Cannot request an extension on a record that is already " + r.getStatus() + ".");
        }
        if (STATUS_PENDING.equals(r.getTargetDateExtensionStatus())) {
            throw AppException.conflict(
                    "An extension request is already pending review.");
        }

        User actor = orgSecurity.currentUser().orElse(null);

        r.setTargetDateExtensionDate(req.getExtensionDate());
        r.setTargetDateExtensionReason(req.getReason());
        r.setTargetDateExtensionStatus(STATUS_PENDING);
        r.setTargetDateExtensionRequestedById(actor != null ? actor.getId() : null);
        r.setTargetDateExtensionRequestedAt(LocalDateTime.now());
        // Clear previous decision metadata in case this is a re-request.
        r.setTargetDateExtensionDecidedById(null);
        r.setTargetDateExtensionDecidedAt(null);

        recordLookup.save(r);
        log.info("Extension request: {} #{} → {}", recordType, recordId, req.getExtensionDate());
        return toResponse(r, null);
    }

    @Transactional
    public TargetDateExtensionResponse decide(QmsRecordType recordType, Long recordId,
                                               TargetDateExtensionDecision dec) {
        QmsRecord r = recordLookup.findByTypeAndId(recordType, recordId);

        if (!STATUS_PENDING.equals(r.getTargetDateExtensionStatus())) {
            throw AppException.badRequest("No pending extension request to decide on.");
        }

        // Authoriser: HOD of the record's dept, QA Reviewer/Head, or SUPER_ADMIN.
        boolean canDecide = orgSecurity.isSuperAdmin()
                || orgSecurity.isCurrentUserHodOf(r.getDepartmentId())
                || orgSecurity.isCurrentUserQaReviewer()
                || orgSecurity.isCurrentUserQaHead();
        if (!canDecide) {
            throw AppException.forbidden(
                    "Only the HOD, QA Reviewer, or QA Head can decide on an extension request.");
        }

        User actor = orgSecurity.currentUser().orElse(null);
        LocalDate extensionDate = r.getTargetDateExtensionDate();

        if (Boolean.TRUE.equals(dec.getApprove())) {
            r.setTargetDateExtensionStatus(STATUS_APPROVED);
            // Bump the record's effective target so overdue checks line up.
            r.setTargetCompletionDate(extensionDate);
        } else {
            r.setTargetDateExtensionStatus(STATUS_REJECTED);
        }
        r.setTargetDateExtensionDecidedById(actor != null ? actor.getId() : null);
        r.setTargetDateExtensionDecidedAt(LocalDateTime.now());

        // Append a human-readable trace into comments.
        String prev = r.getComments() == null ? "" : r.getComments() + "\n";
        r.setComments(prev + "[Target Date Extension "
                + r.getTargetDateExtensionStatus() + " — "
                + extensionDate + "] " + dec.getRemark());

        recordLookup.save(r);
        log.info("Extension {} on {} #{}", r.getTargetDateExtensionStatus(), recordType, recordId);
        return toResponse(r, dec.getRemark());
    }

    // ─── Internals ──────────────────────────────────────────

    private TargetDateExtensionResponse toResponse(QmsRecord r, String decisionRemark) {
        return TargetDateExtensionResponse.builder()
                .recordId(r.getId())
                .previousTargetDate(r.getTargetCompletionDate())
                .extensionDate(r.getTargetDateExtensionDate())
                .reason(r.getTargetDateExtensionReason())
                .status(r.getTargetDateExtensionStatus())
                .requestedById(r.getTargetDateExtensionRequestedById())
                .requestedAt(r.getTargetDateExtensionRequestedAt())
                .decidedById(r.getTargetDateExtensionDecidedById())
                .decidedAt(r.getTargetDateExtensionDecidedAt())
                .decisionRemark(decisionRemark)
                .build();
    }
}
