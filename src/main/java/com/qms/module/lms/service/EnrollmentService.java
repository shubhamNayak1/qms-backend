package com.qms.module.lms.service;

import com.qms.common.exception.AppException;
import com.qms.common.response.PageResponse;
import com.qms.module.lms.dto.request.BulkEnrollmentRequest;
import com.qms.module.lms.dto.request.ContentProgressRequest;
import com.qms.module.lms.dto.request.EnrollmentRequest;
import com.qms.module.lms.dto.request.WaiverRequest;
import com.qms.module.lms.dto.response.EnrollmentResponse;
import com.qms.module.lms.entity.ContentProgress;
import com.qms.module.lms.entity.Enrollment;
import com.qms.module.lms.entity.ProgramContent;
import com.qms.module.lms.entity.TrainingProgram;
import com.qms.module.lms.enums.EnrollmentStatus;
import com.qms.module.lms.enums.ProgramStatus;
import com.qms.module.lms.repository.ContentProgressRepository;
import com.qms.module.lms.repository.EnrollmentRepository;
import com.qms.module.lms.repository.TrainingProgramRepository;
import com.qms.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.qms.common.enums.AuditAction;
import com.qms.common.enums.AuditModule;
import com.qms.module.audit.annotation.Audited;
import com.qms.module.audit.context.AuditContext;
import com.qms.module.audit.context.AuditContextHolder;
import com.qms.module.audit.service.AuditValueSerializer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EnrollmentService {

    private final EnrollmentRepository      enrollmentRepository;
    private final TrainingProgramRepository programRepository;
    private final ContentProgressRepository progressRepository;
    private final CertificateService        certificateService;
    private final AuditValueSerializer      auditSerializer;

    // ── Queries ──────────────────────────────────────────────

    public PageResponse<EnrollmentResponse> search(Long userId, Long programId,
                                                    EnrollmentStatus status, String department,
                                                    Boolean overdue, int page, int size) {
        return PageResponse.of(
                enrollmentRepository.search(userId, programId, status, department, overdue,
                        PageRequest.of(page, size, Sort.by("createdAt").descending()))
                        .map(this::toResponse));
    }

    public EnrollmentResponse getById(Long id) {
        return toResponseWithProgress(findById(id));
    }

    public List<EnrollmentResponse> getMyEnrollments() {
        Long userId = currentUserId();
        if (userId == null) throw AppException.forbidden("Cannot determine current user");
        return enrollmentRepository.findByUserIdAndIsDeletedFalse(userId)
                .stream().map(this::toResponse).toList();
    }

    // ── Enroll ───────────────────────────────────────────────

    @Audited(action = AuditAction.TRAINING_ASSIGNED, module = AuditModule.TRAINING, entityType = "Enrollment", description = "Enrollment record created")
    @Transactional
    public EnrollmentResponse enroll(EnrollmentRequest req) {
        return doEnroll(req.getProgramId(), req.getUserId(), null, null,
                req.getDueDate(), req.getAssignmentReason());
    }

    @Audited(action = AuditAction.TRAINING_ASSIGNED, module = AuditModule.TRAINING, entityType = "Enrollment", description = "Enrollment record created")
    @Transactional
    public List<EnrollmentResponse> bulkEnroll(BulkEnrollmentRequest req) {
        // Pre-validate program exists and status is valid BEFORE the loop.
        // Without this, every user gets silently skipped and the response is [].
        TrainingProgram program = programRepository.findByIdAndIsDeletedFalse(req.getProgramId())
                .orElseThrow(() -> AppException.notFound("Training Program", req.getProgramId()));
        if (program.getStatus() != ProgramStatus.ACTIVE
                && program.getStatus() != ProgramStatus.PLANNED) {
            throw AppException.badRequest(
                    "Cannot bulk enroll in a " + program.getStatus()
                    + " program. Program must be ACTIVE or PLANNED first.");
        }
        if (req.getUserIds() == null || req.getUserIds().isEmpty()) {
            throw AppException.badRequest("userIds list must not be empty");
        }

        String assignedBy = currentUsername();
        List<EnrollmentResponse> results = new ArrayList<>();
        List<Long> skipped = new ArrayList<>();

        for (Long userId : req.getUserIds()) {
            try {
                results.add(doEnroll(req.getProgramId(), userId, null, assignedBy,
                        req.getDueDate(), req.getAssignmentReason()));
            } catch (AppException e) {
                // Only skip per-user errors (e.g. already enrolled). Program errors already thrown above.
                skipped.add(userId);
                log.warn("Skipping userId={} in bulk enroll: {}", userId, e.getMessage());
            }
        }
        log.info("Bulk enrollment: programId={} — {}/{} enrolled, {} skipped",
                req.getProgramId(), results.size(), req.getUserIds().size(), skipped.size());
        return results;
    }

    private EnrollmentResponse doEnroll(Long programId, Long userId,
                                         Long assignedById, String assignedByName,
                                         LocalDate dueDate, String reason) {
        TrainingProgram program = programRepository.findByIdAndIsDeletedFalse(programId)
                .orElseThrow(() -> AppException.notFound("Training Program", programId));

        if (program.getStatus() != ProgramStatus.ACTIVE
                && program.getStatus() != ProgramStatus.PLANNED) {
            throw AppException.badRequest(
                    "Cannot enroll in a " + program.getStatus() + " program. Must be ACTIVE or PLANNED.");
        }

        // Prevent duplicate active enrollment (allow re-enroll after CANCELLED, EXPIRED, RETRAINING)
        enrollmentRepository.findByUserIdAndProgram_IdAndIsDeletedFalse(userId, programId)
                .ifPresent(existing -> {
                    if (existing.getStatus() != EnrollmentStatus.CANCELLED
                            && existing.getStatus() != EnrollmentStatus.EXPIRED
                            && existing.getStatus() != EnrollmentStatus.RETRAINING) {
                        throw AppException.conflict(
                                "User " + userId + " is already enrolled in program " + programId
                                + " with status " + existing.getStatus());
                    }
                });

        LocalDate deadline = dueDate;
        if (deadline == null && program.getCompletionDeadlineDays() != null) {
            deadline = LocalDate.now().plusDays(program.getCompletionDeadlineDays());
        }

        Enrollment enrollment = Enrollment.builder()
                .userId(userId)
                .program(program)
                .status(EnrollmentStatus.ALLOCATED)
                .dueDate(deadline)
                .assignedById(assignedById)
                .assignedByName(assignedByName != null ? assignedByName : currentUsername())
                .assignmentReason(reason)
                .progressPercent(0)
                .attemptsUsed(0)
                .build();

        Enrollment saved = enrollmentRepository.save(enrollment);
        log.info("Enrolled userId={} in programId={} (ALLOCATED)", userId, programId);
        return toResponse(saved);
    }

    // ── Department-wise enrollment ────────────────────────────

    @Audited(action = AuditAction.TRAINING_ASSIGNED, module = AuditModule.TRAINING, entityType = "Enrollment", description = "Department-wise enrollment created")
    @Transactional
    public List<EnrollmentResponse> enrollByDepartment(Long programId, String department,
                                                        LocalDate dueDate, String reason) {
        TrainingProgram program = programRepository.findByIdAndIsDeletedFalse(programId)
                .orElseThrow(() -> AppException.notFound("Training Program", programId));

        if (program.getStatus() != ProgramStatus.ACTIVE
                && program.getStatus() != ProgramStatus.PLANNED) {
            throw AppException.badRequest(
                    "Cannot enroll in a " + program.getStatus() + " program");
        }

        // Find all users in the given department from existing enrollments that may be in other programs,
        // or just use department string as discriminator and create enrollments for each user.
        // For now, this stores the department so the frontend sends explicit userIds after department lookup.
        // Instead, we create a "department placeholder" — caller must send userIds after lookup.
        throw AppException.badRequest(
                "Use POST /bulk with user IDs from department lookup. " +
                "Filter users by department in your user service, then call bulk enroll.");
    }

    // ── Approve allocation (sets program ACTIVE) ──────────────

    @Audited(action = AuditAction.APPROVE, module = AuditModule.TRAINING, entityType = "TrainingProgram",
             entityIdArgIndex = 0, description = "Allocation approved — program activated to ACTIVE")
    @Transactional
    public void approveAllocation(Long programId) {
        TrainingProgram program = programRepository.findByIdAndIsDeletedFalse(programId)
                .orElseThrow(() -> AppException.notFound("Training Program", programId));
        if (program.getStatus() != ProgramStatus.PLANNED) {
            throw AppException.badRequest(
                    "Allocation can only be approved for PLANNED programs. Current: " + program.getStatus());
        }
        program.setStatus(ProgramStatus.ACTIVE);
        programRepository.save(program);
        log.info("Allocation approved for programId={} by {}", programId, currentUsername());
    }

    // ── Progress tracking ────────────────────────────────────

    @Audited(action = AuditAction.UPDATE, module = AuditModule.TRAINING, entityType = "Enrollment", entityIdArgIndex = 0)
    @Transactional
    public EnrollmentResponse updateProgress(Long enrollmentId, ContentProgressRequest req) {
        Enrollment enrollment = findById(enrollmentId);
        AuditContextHolder.set(AuditContext.builder()
                .oldValue(auditSerializer.serialize(toResponse(enrollment)))
                .build());
        assertNotTerminal(enrollment);

        // Start the enrollment if first time accessing
        if (enrollment.getStatus() == EnrollmentStatus.ENROLLED) {
            enrollment.setStatus(EnrollmentStatus.IN_PROGRESS);
            enrollment.setStartedAt(LocalDateTime.now());
        }

        // Upsert ContentProgress
        ContentProgress progress = progressRepository
                .findByEnrollment_IdAndContent_Id(enrollmentId, req.getContentId())
                .orElseGet(() -> {
                    ProgramContent content = enrollment.getProgram().getContents().stream()
                            .filter(c -> c.getId().equals(req.getContentId()))
                            .findFirst()
                            .orElseThrow(() -> AppException.notFound("Content item", req.getContentId()));
                    return ContentProgress.builder()
                            .enrollment(enrollment).content(content)
                            .firstAccessedAt(LocalDateTime.now())
                            .build();
                });

        progress.setLastAccessedAt(LocalDateTime.now());
        if (req.getViewPercent() != null) {
            progress.setViewPercent(Math.max(progress.getViewPercent(), req.getViewPercent()));
        }
        if (Boolean.TRUE.equals(req.getAcknowledged())) {
            progress.setAcknowledged(true);
        }
        if (req.getSessionTimeSeconds() != null) {
            progress.setTimeSpentSeconds(progress.getTimeSpentSeconds() + req.getSessionTimeSeconds());
        }
        // Auto-complete when 100% viewed or acknowledged
        if (!progress.getIsCompleted()
                && (Integer.valueOf(100).equals(progress.getViewPercent())
                    || Boolean.TRUE.equals(progress.getAcknowledged()))) {
            progress.markCompleted();
        }
        progressRepository.save(progress);

        // Recalculate overall enrollment progress
        recalculateProgress(enrollment);
        return toResponseWithProgress(enrollmentRepository.save(enrollment));
    }

    // ── Waiver ───────────────────────────────────────────────

    @Audited(action = AuditAction.UPDATE, module = AuditModule.TRAINING, entityType = "Enrollment", entityIdArgIndex = 0)
    @Transactional
    public EnrollmentResponse grantWaiver(Long enrollmentId, WaiverRequest req) {
        Enrollment enrollment = findById(enrollmentId);
        if (enrollment.getStatus() == EnrollmentStatus.COMPLETED
                || enrollment.getStatus() == EnrollmentStatus.CANCELLED) {
            throw AppException.badRequest(
                    "Cannot waive a " + enrollment.getStatus() + " enrollment");
        }
        enrollment.setStatus(EnrollmentStatus.WAIVED);
        enrollment.setWaiverReason(req.getReason());
        enrollment.setWaivedByName(currentUsername());
        enrollment.setWaivedAt(LocalDateTime.now());
        log.info("Waiver granted for enrollmentId={} by {}", enrollmentId, currentUsername());
        return toResponse(enrollmentRepository.save(enrollment));
    }

    // ── Cancel ───────────────────────────────────────────────

    @Audited(action = AuditAction.CANCEL, module = AuditModule.TRAINING, entityType = "Enrollment", entityIdArgIndex = 0)
    @Transactional
    public EnrollmentResponse cancel(Long enrollmentId, String reason) {
        Enrollment enrollment = findById(enrollmentId);
        AuditContextHolder.set(AuditContext.builder()
                .oldValue(auditSerializer.serialize(toResponse(enrollment)))
                .build());
        if (enrollment.getStatus() == EnrollmentStatus.COMPLETED
                || enrollment.getStatus() == EnrollmentStatus.CANCELLED) {
            throw AppException.badRequest("Cannot cancel a " + enrollment.getStatus() + " enrollment");
        }
        enrollment.setStatus(EnrollmentStatus.CANCELLED);
        enrollment.setAssignmentReason(reason);
        log.info("Enrollment {} cancelled", enrollmentId);
        return toResponse(enrollmentRepository.save(enrollment));
    }

    // ── Internal completion trigger ───────────────────────────

    /**
     * Called by AssessmentService after a passing attempt, or directly if no assessment required.
     * Marks enrollment COMPLETED, issues certificate, updates all completion timestamps.
     */
    @Audited(action = AuditAction.TRAINING_COMPLETED, module = AuditModule.TRAINING, entityType = "Enrollment", description = "Enrollment completed")
    @Transactional
    public void completeEnrollment(Enrollment enrollment, Integer finalScore) {
        enrollment.setStatus(EnrollmentStatus.COMPLETED);
        enrollment.setCompletedAt(LocalDateTime.now());
        enrollment.setProgressPercent(100);
        if (finalScore != null) enrollment.setLastScore(finalScore);
        enrollmentRepository.save(enrollment);

        // Issue certificate
        certificateService.issue(enrollment, finalScore);
        log.info("Enrollment {} completed for userId={}", enrollment.getId(), enrollment.getUserId());
    }

    // ── Helpers ──────────────────────────────────────────────

    Enrollment findById(Long id) {
        return enrollmentRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> AppException.notFound("Enrollment", id));
    }

    private void recalculateProgress(Enrollment enrollment) {
        List<ProgramContent> required = enrollment.getProgram().getContents().stream()
                .filter(ProgramContent::getIsRequired).toList();
        if (required.isEmpty()) {
            enrollment.setProgressPercent(100);
            return;
        }
        long done = progressRepository.countByEnrollment_IdAndIsCompletedTrue(enrollment.getId());
        int percent = (int) Math.round(done * 100.0 / required.size());
        enrollment.setProgressPercent(Math.min(percent, 100));

        // Auto-complete if no assessment required and all content done
        if (percent >= 100
                && !enrollment.getProgram().getAssessmentRequired()
                && enrollment.getStatus() == EnrollmentStatus.IN_PROGRESS) {
            completeEnrollment(enrollment, null);
        }
    }

    private void assertNotTerminal(Enrollment e) {
        if (e.getStatus() == EnrollmentStatus.COMPLETED
                || e.getStatus() == EnrollmentStatus.CANCELLED
                || e.getStatus() == EnrollmentStatus.WAIVED) {
            throw AppException.badRequest("Cannot update progress on a " + e.getStatus() + " enrollment");
        }
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        try {
            return (Long) auth.getPrincipal().getClass().getMethod("getId").invoke(auth.getPrincipal());
        } catch (Exception e) { return null; }
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "SYSTEM";
    }

    EnrollmentResponse toResponse(Enrollment e) {
        return EnrollmentResponse.builder()
                .id(e.getId()).userId(e.getUserId()).userName(e.getUserName())
                .userEmail(e.getUserEmail()).userDepartment(e.getUserDepartment())
                .programId(e.getProgram() != null ? e.getProgram().getId() : null)
                .programCode(e.getProgram() != null ? e.getProgram().getCode() : null)
                .programTitle(e.getProgram() != null ? e.getProgram().getTitle() : null)
                .status(e.getStatus()).dueDate(e.getDueDate())
                .startedAt(e.getStartedAt()).completedAt(e.getCompletedAt())
                .progressPercent(e.getProgressPercent())
                .attemptsUsed(e.getAttemptsUsed()).lastScore(e.getLastScore())
                .assignedByName(e.getAssignedByName()).assignmentReason(e.getAssignmentReason())
                .waiverReason(e.getWaiverReason()).waivedByName(e.getWaivedByName())
                .attendanceMarked(e.getAttendanceMarked())
                .attendanceDate(e.getAttendanceDate())
                .complianceSubmittedAt(e.getComplianceSubmittedAt())
                .complianceReviewedAt(e.getComplianceReviewedAt())
                .complianceReviewedBy(e.getComplianceReviewedBy())
                .retrainingOfEnrollmentId(e.getRetrainingOfEnrollmentId())
                .overdue(e.isOverdue()).compliant(e.isCompliant())
                .createdAt(e.getCreatedAt()).updatedAt(e.getUpdatedAt())
                .build();
    }

    private EnrollmentResponse toResponseWithProgress(Enrollment e) {
        List<EnrollmentResponse.ContentProgressSummary> progressSummary =
                progressRepository.findByEnrollment_IdOrderByContent_DisplayOrderAsc(e.getId())
                        .stream()
                        .map(cp -> EnrollmentResponse.ContentProgressSummary.builder()
                                .contentId(cp.getContent().getId())
                                .contentTitle(cp.getContent().getTitle())
                                .contentType(cp.getContent().getContentType().name())
                                .isCompleted(cp.getIsCompleted()).acknowledged(cp.getAcknowledged())
                                .viewPercent(cp.getViewPercent())
                                .displayOrder(cp.getContent().getDisplayOrder())
                                .isRequired(cp.getContent().getIsRequired())
                                .build())
                        .toList();

        EnrollmentResponse resp = toResponse(e);
        resp.setContentProgress(progressSummary);
        if (e.getCertificate() != null) {
            resp.setCertificate(certificateService.toResponse(e.getCertificate()));
        }
        return resp;
    }
}
