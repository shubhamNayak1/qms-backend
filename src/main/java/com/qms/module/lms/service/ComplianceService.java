package com.qms.module.lms.service;

import com.qms.common.exception.AppException;
import com.qms.module.lms.dto.request.ComplianceReviewRequest;
import com.qms.module.lms.dto.request.ComplianceSubmissionRequest;
import com.qms.module.lms.dto.request.TniRequest;
import com.qms.module.lms.dto.response.ComplianceSubmissionResponse;
import com.qms.module.lms.dto.response.TniResponse;
import com.qms.module.lms.entity.*;
import com.qms.module.lms.enums.ComplianceStatus;
import com.qms.module.lms.enums.EnrollmentStatus;
import com.qms.module.lms.enums.TrainingType;
import com.qms.module.lms.repository.*;
import com.qms.common.enums.AuditAction;
import com.qms.common.enums.AuditModule;
import com.qms.module.audit.annotation.Audited;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ComplianceService {

    private final ComplianceSubmissionRepository      submissionRepository;
    private final ComplianceReviewRepository          reviewRepository;
    private final EnrollmentRepository                enrollmentRepository;
    private final EnrollmentService                   enrollmentService;
    private final CertificateService                  certificateService;
    private final TrainingNeedIdentificationRepository tniRepository;

    // ── Step 6: Trainee submits compliance ───────────────────

    @Audited(action = AuditAction.SUBMIT, module = AuditModule.TRAINING, entityType = "ComplianceSubmission",
             entityIdArgIndex = 0, description = "Trainee submitted compliance evidence")
    @Transactional
    public ComplianceSubmissionResponse submit(Long enrollmentId, ComplianceSubmissionRequest req) {
        Enrollment enrollment = enrollmentService.findById(enrollmentId);

        if (enrollment.getStatus() != EnrollmentStatus.IN_PROGRESS) {
            throw AppException.badRequest(
                    "Compliance can only be submitted when enrollment is IN_PROGRESS. Current: "
                    + enrollment.getStatus());
        }
        if (submissionRepository.existsByEnrollment_Id(enrollmentId)) {
            throw AppException.conflict("Compliance already submitted for this enrollment");
        }

        ComplianceSubmission submission = ComplianceSubmission.builder()
                .enrollment(enrollment)
                .attachmentStorageKey(req.getAttachmentStorageKey())
                .attachmentFileName(req.getAttachmentFileName())
                .attachmentFileSizeBytes(req.getAttachmentFileSizeBytes())
                .qnaAnswers(req.getQnaAnswers())
                .status(ComplianceStatus.PENDING)
                .build();

        submissionRepository.save(submission);

        enrollment.setStatus(EnrollmentStatus.PENDING_REVIEW);
        enrollment.setComplianceSubmittedAt(LocalDateTime.now());
        enrollmentRepository.save(enrollment);

        log.info("Compliance submitted for enrollmentId={} by userId={}",
                enrollmentId, enrollment.getUserId());
        return toResponse(submission);
    }

    // ── Step 7a: Coordinator/Trainer review ──────────────────

    @Audited(action = AuditAction.APPROVE, module = AuditModule.TRAINING, entityType = "ComplianceSubmission",
             entityIdArgIndex = 0, description = "Coordinator/Trainer reviewed compliance submission")
    @Transactional
    public ComplianceSubmissionResponse coordinatorReview(Long enrollmentId,
                                                           ComplianceReviewRequest req) {
        Enrollment enrollment = enrollmentService.findById(enrollmentId);

        if (enrollment.getStatus() != EnrollmentStatus.PENDING_REVIEW) {
            throw AppException.badRequest(
                    "Enrollment must be in PENDING_REVIEW state. Current: " + enrollment.getStatus());
        }

        ComplianceSubmission submission = submissionRepository.findByEnrollment_Id(enrollmentId)
                .orElseThrow(() -> AppException.notFound(
                        "Compliance submission for enrollment", enrollmentId));

        String reviewer = currentUsername();
        saveReview(submission, reviewer, "COORDINATOR", req.getDecision(), req.getComments());

        if ("APPROVED".equals(req.getDecision())) {
            submission.setStatus(ComplianceStatus.APPROVED);
            submissionRepository.save(submission);

            TrainingType type = enrollment.getProgram().getTrainingType();

            if (type == TrainingType.INDUCTION) {
                // Induction: move to HR review
                enrollment.setStatus(EnrollmentStatus.PENDING_HR_REVIEW);
            } else if (Boolean.TRUE.equals(enrollment.getProgram().getExamEnabled())) {
                // Exam enabled: stay as PENDING_REVIEW (exam will follow)
                // Actually we move to a transient "compliance approved" state;
                // exam will be taken independently via AssessmentController
                enrollment.setStatus(EnrollmentStatus.IN_PROGRESS);
            } else {
                // No exam: directly complete
                enrollmentService.completeEnrollment(enrollment, null);
            }
        } else {
            // REJECTED
            submission.setStatus(ComplianceStatus.REJECTED);
            submission.setRejectionReason(req.getComments());
            submissionRepository.save(submission);
            failAndRetrain(enrollment, "Compliance rejected by coordinator");
        }

        enrollment.setComplianceReviewedAt(LocalDateTime.now());
        enrollment.setComplianceReviewedBy(reviewer);
        enrollmentRepository.save(enrollment);

        log.info("Coordinator review for enrollmentId={}: {}", enrollmentId, req.getDecision());
        return toResponse(submission);
    }

    // ── Step 7b: HR review (Induction only) ─────────────────

    @Audited(action = AuditAction.APPROVE, module = AuditModule.TRAINING, entityType = "ComplianceSubmission",
             entityIdArgIndex = 0, description = "HR reviewed induction compliance submission")
    @Transactional
    public ComplianceSubmissionResponse hrReview(Long enrollmentId, ComplianceReviewRequest req) {
        Enrollment enrollment = enrollmentService.findById(enrollmentId);

        assertInductionType(enrollment);
        if (enrollment.getStatus() != EnrollmentStatus.PENDING_HR_REVIEW) {
            throw AppException.badRequest(
                    "Enrollment must be in PENDING_HR_REVIEW state. Current: " + enrollment.getStatus());
        }

        ComplianceSubmission submission = submissionRepository.findByEnrollment_Id(enrollmentId)
                .orElseThrow(() -> AppException.notFound(
                        "Compliance submission for enrollment", enrollmentId));

        String reviewer = currentUsername();
        saveReview(submission, reviewer, "HR", req.getDecision(), req.getComments());

        if ("APPROVED".equals(req.getDecision())) {
            enrollment.setStatus(EnrollmentStatus.PENDING_QA_APPROVAL);
        } else {
            submission.setStatus(ComplianceStatus.REJECTED);
            submission.setRejectionReason(req.getComments());
            submissionRepository.save(submission);
            failAndRetrain(enrollment, "Compliance rejected at HR review");
        }

        enrollmentRepository.save(enrollment);
        log.info("HR review for enrollmentId={}: {}", enrollmentId, req.getDecision());
        return toResponse(submission);
    }

    // ── Step 7c: QA Head approval (Induction only) ──────────

    @Audited(action = AuditAction.APPROVE, module = AuditModule.TRAINING, entityType = "ComplianceSubmission",
             entityIdArgIndex = 0, description = "QA Head final approval for induction compliance")
    @Transactional
    public ComplianceSubmissionResponse qaHeadApprove(Long enrollmentId,
                                                       ComplianceReviewRequest req,
                                                       TniRequest tniData) {
        Enrollment enrollment = enrollmentService.findById(enrollmentId);

        assertInductionType(enrollment);
        if (enrollment.getStatus() != EnrollmentStatus.PENDING_QA_APPROVAL) {
            throw AppException.badRequest(
                    "Enrollment must be in PENDING_QA_APPROVAL state. Current: " + enrollment.getStatus());
        }

        ComplianceSubmission submission = submissionRepository.findByEnrollment_Id(enrollmentId)
                .orElseThrow(() -> AppException.notFound(
                        "Compliance submission for enrollment", enrollmentId));

        String reviewer = currentUsername();
        saveReview(submission, reviewer, "QA_HEAD", req.getDecision(), req.getComments());

        if ("APPROVED".equals(req.getDecision())) {
            submission.setStatus(ComplianceStatus.APPROVED);
            submissionRepository.save(submission);

            // Complete the enrollment and issue certificate
            enrollmentService.completeEnrollment(enrollment, null);

            // Auto-generate TNI
            generateTni(enrollment, tniData, reviewer);
            log.info("QA Head approved induction for enrollmentId={} — TNI generated", enrollmentId);
        } else {
            submission.setStatus(ComplianceStatus.REJECTED);
            submission.setRejectionReason(req.getComments());
            submissionRepository.save(submission);
            failAndRetrain(enrollment, "Compliance rejected at QA Head approval");
        }

        return toResponse(submission);
    }

    // ── TNI ──────────────────────────────────────────────────

    public TniResponse getTni(Long enrollmentId) {
        return tniRepository.findByEnrollmentId(enrollmentId)
                .map(this::toTniResponse)
                .orElseThrow(() -> AppException.notFound(
                        "TNI for enrollment", enrollmentId));
    }

    public List<TniResponse> getTniByUser(Long userId) {
        return tniRepository.findByUserId(userId)
                .stream().map(this::toTniResponse).toList();
    }

    @Audited(action = AuditAction.UPDATE, module = AuditModule.TRAINING, entityType = "TrainingNeedIdentification",
             entityIdArgIndex = 0, description = "TNI record updated")
    @Transactional
    public TniResponse updateTni(Long enrollmentId, TniRequest req) {
        TrainingNeedIdentification tni = tniRepository.findByEnrollmentId(enrollmentId)
                .orElseThrow(() -> AppException.notFound("TNI for enrollment", enrollmentId));
        if (req.getJobDescription()       != null) tni.setJobDescription(req.getJobDescription());
        if (req.getIdentifiedGaps()       != null) tni.setIdentifiedGaps(req.getIdentifiedGaps());
        if (req.getRecommendedTrainings() != null) tni.setRecommendedTrainings(req.getRecommendedTrainings());
        if (req.getDesignation()          != null) tni.setDesignation(req.getDesignation());
        if (req.getNotes()                != null) tni.setNotes(req.getNotes());
        return toTniResponse(tniRepository.save(tni));
    }

    // ── Queries ──────────────────────────────────────────────

    public ComplianceSubmissionResponse getByEnrollment(Long enrollmentId) {
        return toResponse(submissionRepository.findByEnrollment_Id(enrollmentId)
                .orElseThrow(() -> AppException.notFound(
                        "Compliance submission for enrollment", enrollmentId)));
    }

    public List<ComplianceSubmissionResponse> getPendingReviews() {
        return submissionRepository.findByStatus(ComplianceStatus.PENDING)
                .stream().map(this::toResponse).toList();
    }

    // ── Internal helpers ──────────────────────────────────────

    /** Create a review record for the submission. */
    private void saveReview(ComplianceSubmission submission, String reviewer,
                             String role, String decision, String comments) {
        // Remove existing review (re-review case)
        reviewRepository.findBySubmission_Id(submission.getId())
                .ifPresent(reviewRepository::delete);

        ComplianceReview review = ComplianceReview.builder()
                .submission(submission)
                .reviewerName(reviewer)
                .reviewerRole(role)
                .decision(decision)
                .comments(comments)
                .build();
        reviewRepository.save(review);
    }

    /** Mark enrollment FAILED and create a new ALLOCATED retraining enrollment. */
    private void failAndRetrain(Enrollment enrollment, String reason) {
        enrollment.setStatus(EnrollmentStatus.FAILED);
        enrollmentRepository.save(enrollment);

        Enrollment retraining = Enrollment.builder()
                .userId(enrollment.getUserId())
                .userName(enrollment.getUserName())
                .userEmail(enrollment.getUserEmail())
                .userDepartment(enrollment.getUserDepartment())
                .program(enrollment.getProgram())
                .status(EnrollmentStatus.ALLOCATED)
                .retrainingOfEnrollmentId(enrollment.getId())
                .assignedByName("SYSTEM")
                .assignmentReason("Auto-retraining: " + reason)
                .dueDate(enrollment.getProgram().getCompletionDeadlineDays() != null
                        ? LocalDate.now().plusDays(enrollment.getProgram().getCompletionDeadlineDays())
                        : null)
                .progressPercent(0)
                .attemptsUsed(0)
                .build();
        enrollmentRepository.save(retraining);
        log.info("Retraining enrollment created for userId={} programId={} reason={}",
                enrollment.getUserId(), enrollment.getProgram().getId(), reason);
    }

    /** Generate TNI record after induction approval. */
    private void generateTni(Enrollment enrollment, TniRequest req, String generatedBy) {
        if (tniRepository.findByEnrollmentId(enrollment.getId()).isPresent()) return; // idempotent

        TrainingNeedIdentification tni = TrainingNeedIdentification.builder()
                .enrollmentId(enrollment.getId())
                .userId(enrollment.getUserId())
                .userName(enrollment.getUserName())
                .department(enrollment.getUserDepartment())
                .designation(req != null ? req.getDesignation() : null)
                .jobDescription(req != null ? req.getJobDescription() : null)
                .identifiedGaps(req != null ? req.getIdentifiedGaps() : null)
                .recommendedTrainings(req != null ? req.getRecommendedTrainings() : null)
                .notes(req != null ? req.getNotes() : null)
                .generatedBy(generatedBy)
                .build();
        tniRepository.save(tni);
    }

    private void assertInductionType(Enrollment enrollment) {
        if (enrollment.getProgram().getTrainingType() != TrainingType.INDUCTION) {
            throw AppException.badRequest(
                    "This action is only applicable to INDUCTION training programs");
        }
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "SYSTEM";
    }

    // ── Response mappers ──────────────────────────────────────

    ComplianceSubmissionResponse toResponse(ComplianceSubmission s) {
        ComplianceSubmissionResponse.ComplianceSubmissionResponseBuilder builder =
                ComplianceSubmissionResponse.builder()
                        .id(s.getId())
                        .enrollmentId(s.getEnrollment() != null ? s.getEnrollment().getId() : null)
                        .userId(s.getEnrollment() != null ? s.getEnrollment().getUserId() : null)
                        .userName(s.getEnrollment() != null ? s.getEnrollment().getUserName() : null)
                        .attachmentStorageKey(s.getAttachmentStorageKey())
                        .attachmentFileName(s.getAttachmentFileName())
                        .attachmentFileSizeBytes(s.getAttachmentFileSizeBytes())
                        .qnaAnswers(s.getQnaAnswers())
                        .status(s.getStatus())
                        .rejectionReason(s.getRejectionReason())
                        .submittedAt(s.getSubmittedAt())
                        .updatedAt(s.getUpdatedAt());

        if (s.getReview() != null) {
            ComplianceReview r = s.getReview();
            builder.reviewDecision(r.getDecision())
                   .reviewerName(r.getReviewerName())
                   .reviewerRole(r.getReviewerRole())
                   .reviewComments(r.getComments())
                   .reviewedAt(r.getReviewedAt());
        }
        return builder.build();
    }

    TniResponse toTniResponse(TrainingNeedIdentification t) {
        return TniResponse.builder()
                .id(t.getId())
                .enrollmentId(t.getEnrollmentId())
                .userId(t.getUserId())
                .userName(t.getUserName())
                .department(t.getDepartment())
                .designation(t.getDesignation())
                .jobDescription(t.getJobDescription())
                .identifiedGaps(t.getIdentifiedGaps())
                .recommendedTrainings(t.getRecommendedTrainings())
                .notes(t.getNotes())
                .generatedBy(t.getGeneratedBy())
                .generatedAt(t.getGeneratedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }
}
