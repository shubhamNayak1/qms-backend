package com.qms.module.lms.service;

import com.qms.common.exception.AppException;
import com.qms.common.response.PageResponse;
import com.qms.module.lms.dto.request.ProgramRequest;
import com.qms.module.lms.dto.response.ProgramResponse;
import com.qms.module.lms.entity.ProgramContent;
import com.qms.module.lms.entity.ProgramDocumentLink;
import com.qms.module.lms.entity.TrainingProgram;
import com.qms.module.lms.enums.ContentType;
import com.qms.module.lms.enums.ProgramStatus;
import com.qms.module.lms.repository.EnrollmentRepository;
import com.qms.module.lms.repository.TrainingAttendanceRepository;
import com.qms.module.lms.repository.TrainingProgramRepository;
import com.qms.module.lms.repository.TrainingSessionRepository;
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

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrainingProgramService {

    private final TrainingProgramRepository  programRepository;
    private final EnrollmentRepository       enrollmentRepository;
    private final TrainingSessionRepository  sessionRepository;
    private final TrainingAttendanceRepository attendanceRepository;
    private final AuditValueSerializer       auditSerializer;

    // ── Queries ──────────────────────────────────────────────

    public PageResponse<ProgramResponse> search(ProgramStatus status, String category,
                                                 String department, Boolean mandatory,
                                                 String search, int page, int size) {
        String searchPattern = (search != null && !search.isBlank())
                ? "%" + search.toLowerCase() + "%" : null;
        return PageResponse.of(
                programRepository.search(status, category, department, mandatory, searchPattern,
                        PageRequest.of(page, size, Sort.by("createdAt").descending()))
                        .map(this::toResponse));
    }

    public ProgramResponse getById(Long id) {
        return toResponse(findById(id));
    }

    public ProgramResponse getByCode(String code) {
        return toResponse(programRepository.findByCodeAndIsDeletedFalse(code)
                .orElseThrow(() -> AppException.notFound("Training Program with code: " + code)));
    }

    // ── Create ───────────────────────────────────────────────

    @Audited(action = AuditAction.CREATE, module = AuditModule.TRAINING, entityType = "TrainingProgram", description = "TrainingProgram record created")
    @Transactional
    public ProgramResponse create(ProgramRequest req) {
        if (programRepository.existsByCodeAndIsDeletedFalse(req.getCode())) {
            throw AppException.conflict("A program with code '" + req.getCode() + "' already exists");
        }
        String username = currentUsername();
        boolean examEnabled = Boolean.TRUE.equals(req.getExamEnabled())
                || Boolean.TRUE.equals(req.getAssessmentRequired());

        TrainingProgram program = TrainingProgram.builder()
                .code(req.getCode().toUpperCase())
                .title(req.getTitle())
                .description(req.getDescription())
                .trainingType(req.getTrainingType())
                .trainingSubType(req.getTrainingSubType())
                .category(req.getCategory())
                .department(req.getDepartment())
                .departments(req.getDepartments())
                .tags(req.getTags())
                .status(ProgramStatus.DRAFT)
                .isMandatory(req.getIsMandatory() != null ? req.getIsMandatory() : false)
                .trainerId(req.getTrainerId())
                .trainerName(req.getTrainerName())
                .vendorName(req.getVendorName())
                .coordinatorId(req.getCoordinatorId())
                .coordinatorName(req.getCoordinatorName())
                .location(req.getLocation())
                .conferenceLink(req.getConferenceLink())
                .examEnabled(examEnabled)
                .assessmentRequired(examEnabled)
                .estimatedDurationMinutes(req.getEstimatedDurationMinutes())
                .certificateValidityYears(req.getCertificateValidityYears())
                .completionDeadlineDays(req.getCompletionDeadlineDays())
                .passScore(req.getPassScore() != null ? req.getPassScore() : 80)
                .maxAttempts(req.getMaxAttempts() != null ? req.getMaxAttempts() : 3)
                .ownerId(req.getOwnerId())
                .ownerName(username)
                .createdByName(username)
                .build();

        TrainingProgram saved = programRepository.save(program);
        log.info("Training program created: {} by {}", saved.getCode(), username);
        return toResponse(saved);
    }

    // ── Update ───────────────────────────────────────────────

    @Audited(action = AuditAction.UPDATE, module = AuditModule.TRAINING, entityType = "TrainingProgram", entityIdArgIndex = 0)
    @Transactional
    public ProgramResponse update(Long id, ProgramRequest req) {
        TrainingProgram p = findById(id);
        AuditContextHolder.set(AuditContext.builder()
                .oldValue(auditSerializer.serialize(toResponse(p)))
                .build());
        if (p.getStatus() == ProgramStatus.ARCHIVED) {
            throw AppException.badRequest("Archived programs cannot be edited");
        }
        if (!p.getCode().equals(req.getCode())
                && programRepository.existsByCodeAndIsDeletedFalse(req.getCode())) {
            throw AppException.conflict("A program with code '" + req.getCode() + "' already exists");
        }
        if (req.getCode()                       != null) p.setCode(req.getCode().toUpperCase());
        if (req.getTitle()                      != null) p.setTitle(req.getTitle());
        if (req.getDescription()                != null) p.setDescription(req.getDescription());
        if (req.getTrainingType()               != null) p.setTrainingType(req.getTrainingType());
        if (req.getTrainingSubType()            != null) p.setTrainingSubType(req.getTrainingSubType());
        if (req.getCategory()                   != null) p.setCategory(req.getCategory());
        if (req.getDepartment()                 != null) p.setDepartment(req.getDepartment());
        if (req.getDepartments()                != null) p.setDepartments(req.getDepartments());
        if (req.getTags()                       != null) p.setTags(req.getTags());
        if (req.getIsMandatory()                != null) p.setIsMandatory(req.getIsMandatory());
        if (req.getTrainerId()                  != null) p.setTrainerId(req.getTrainerId());
        if (req.getTrainerName()                != null) p.setTrainerName(req.getTrainerName());
        if (req.getVendorName()                 != null) p.setVendorName(req.getVendorName());
        if (req.getCoordinatorId()              != null) p.setCoordinatorId(req.getCoordinatorId());
        if (req.getCoordinatorName()            != null) p.setCoordinatorName(req.getCoordinatorName());
        if (req.getLocation()                   != null) p.setLocation(req.getLocation());
        if (req.getConferenceLink()             != null) p.setConferenceLink(req.getConferenceLink());
        if (req.getExamEnabled()                != null) {
            p.setExamEnabled(req.getExamEnabled());
            p.setAssessmentRequired(req.getExamEnabled());
        }
        if (req.getEstimatedDurationMinutes()   != null) p.setEstimatedDurationMinutes(req.getEstimatedDurationMinutes());
        if (req.getCertificateValidityYears()   != null) p.setCertificateValidityYears(req.getCertificateValidityYears());
        if (req.getCompletionDeadlineDays()     != null) p.setCompletionDeadlineDays(req.getCompletionDeadlineDays());
        if (req.getPassScore()                  != null) p.setPassScore(req.getPassScore());
        if (req.getMaxAttempts()                != null) p.setMaxAttempts(req.getMaxAttempts());
        if (req.getOwnerId()                    != null) p.setOwnerId(req.getOwnerId());
        return toResponse(programRepository.save(p));
    }

    // ── Status transitions ───────────────────────────────────

    @Audited(action = AuditAction.UPDATE, module = AuditModule.TRAINING, entityType = "TrainingProgram", entityIdArgIndex = 0)
    @Transactional
    public ProgramResponse raiseForReview(Long id) {
        TrainingProgram p = findById(id);
        if (p.getStatus() != ProgramStatus.DRAFT && p.getStatus() != ProgramStatus.REJECTED) {
            throw AppException.badRequest("Only DRAFT or REJECTED programs can be submitted for review. Current: " + p.getStatus());
        }
        p.setStatus(ProgramStatus.UNDER_REVIEW);
        p.setRejectionReason(null);
        log.info("Training program {} raised for review by {}", p.getCode(), currentUsername());
        return toResponse(programRepository.save(p));
    }

    @Audited(action = AuditAction.APPROVE, module = AuditModule.TRAINING, entityType = "TrainingProgram", entityIdArgIndex = 0)
    @Transactional
    public ProgramResponse approve(Long id) {
        TrainingProgram p = findById(id);
        if (p.getStatus() != ProgramStatus.UNDER_REVIEW) {
            throw AppException.badRequest("Only UNDER_REVIEW programs can be approved. Current: " + p.getStatus());
        }
        p.setStatus(ProgramStatus.APPROVED);
        log.info("Training program {} approved by {}", p.getCode(), currentUsername());
        return toResponse(programRepository.save(p));
    }

    @Audited(action = AuditAction.UPDATE, module = AuditModule.TRAINING, entityType = "TrainingProgram", entityIdArgIndex = 0)
    @Transactional
    public ProgramResponse reject(Long id, String reason) {
        TrainingProgram p = findById(id);
        if (p.getStatus() != ProgramStatus.UNDER_REVIEW) {
            throw AppException.badRequest("Only UNDER_REVIEW programs can be rejected. Current: " + p.getStatus());
        }
        p.setStatus(ProgramStatus.REJECTED);
        p.setRejectionReason(reason);
        log.info("Training program {} rejected by {}", p.getCode(), currentUsername());
        return toResponse(programRepository.save(p));
    }

    @Audited(action = AuditAction.UPDATE, module = AuditModule.TRAINING, entityType = "TrainingProgram", entityIdArgIndex = 0)
    @Transactional
    public ProgramResponse markPlanned(Long id) {
        TrainingProgram p = findById(id);
        if (p.getStatus() != ProgramStatus.APPROVED) {
            throw AppException.badRequest("Only APPROVED programs can be planned. Current: " + p.getStatus());
        }
        if (sessionRepository.countActiveSessionsByProgram(id) == 0) {
            throw AppException.badRequest("Add at least one training session before marking as Planned");
        }
        p.setStatus(ProgramStatus.PLANNED);
        log.info("Training program {} marked as PLANNED", p.getCode());
        return toResponse(programRepository.save(p));
    }

    @Audited(action = AuditAction.PUBLISH, module = AuditModule.TRAINING, entityType = "TrainingProgram", entityIdArgIndex = 0)
    @Transactional
    public ProgramResponse activate(Long id) {
        TrainingProgram p = findById(id);
        if (p.getStatus() != ProgramStatus.PLANNED) {
            throw AppException.badRequest("Only PLANNED programs can be activated. Current: " + p.getStatus());
        }
        p.setStatus(ProgramStatus.ACTIVE);
        log.info("Training program {} activated", p.getCode());
        return toResponse(programRepository.save(p));
    }

    /** Legacy publish — kept for backward compatibility. Equivalent to raiseForReview → approve → activate. */
    @Audited(action = AuditAction.PUBLISH, module = AuditModule.TRAINING, entityType = "TrainingProgram", entityIdArgIndex = 0)
    @Transactional
    public ProgramResponse publish(Long id) {
        TrainingProgram p = findById(id);
        if (p.getStatus() != ProgramStatus.DRAFT) {
            throw AppException.badRequest("Only DRAFT programs can be published. Current: " + p.getStatus());
        }
        if (p.getContents().isEmpty()) {
            throw AppException.badRequest("Cannot publish a program with no content items");
        }
        p.setStatus(ProgramStatus.ACTIVE);
        log.info("Training program published (legacy): {}", p.getCode());
        return toResponse(programRepository.save(p));
    }

    @Audited(action = AuditAction.UPDATE, module = AuditModule.TRAINING, entityType = "TrainingProgram", entityIdArgIndex = 0)
    @Transactional
    public ProgramResponse complete(Long id) {
        TrainingProgram p = findById(id);
        if (p.getStatus() != ProgramStatus.ACTIVE) {
            throw AppException.badRequest("Only ACTIVE programs can be completed. Current: " + p.getStatus());
        }
        p.setStatus(ProgramStatus.COMPLETED);
        log.info("Training program {} marked as COMPLETED", p.getCode());
        return toResponse(programRepository.save(p));
    }

    @Audited(action = AuditAction.UPDATE, module = AuditModule.TRAINING, entityType = "TrainingProgram", entityIdArgIndex = 0)
    @Transactional
    public ProgramResponse archive(Long id) {
        TrainingProgram p = findById(id);
        if (p.getStatus() == ProgramStatus.ARCHIVED) {
            throw AppException.badRequest("Program is already archived");
        }
        p.setStatus(ProgramStatus.ARCHIVED);
        log.info("Training program archived: {}", p.getCode());
        return toResponse(programRepository.save(p));
    }

    // ── Content management ───────────────────────────────────

    @Audited(action = AuditAction.UPDATE, module = AuditModule.TRAINING, entityType = "TrainingProgram", entityIdArgIndex = 0)
    @Transactional
    public ProgramResponse addContent(Long programId, String title, ContentType type,
                                       String contentUrl, Long dmsDocumentId, String dmsDocNumber,
                                       String dmsDocVersion, String inlineContent,
                                       Integer durationMinutes, Boolean required) {
        TrainingProgram p = findById(programId);
        if (p.isTerminal()) {
            throw AppException.badRequest("Cannot add content to a " + p.getStatus() + " program");
        }
        int nextOrder = p.getContents().size() + 1;
        ProgramContent item = ProgramContent.builder()
                .program(p).contentType(type).title(title)
                .displayOrder(nextOrder)
                .isRequired(required != null ? required : true)
                .durationMinutes(durationMinutes)
                .contentUrl(contentUrl)
                .dmsDocumentId(dmsDocumentId).dmsDocNumber(dmsDocNumber).dmsDocVersion(dmsDocVersion)
                .inlineContent(inlineContent)
                .build();
        p.getContents().add(item);
        return toResponse(programRepository.save(p));
    }

    @Audited(action = AuditAction.UPDATE, module = AuditModule.TRAINING, entityType = "TrainingProgram", entityIdArgIndex = 0)
    @Transactional
    public ProgramResponse removeContent(Long programId, Long contentId) {
        TrainingProgram p = findById(programId);
        p.getContents().removeIf(c -> c.getId().equals(contentId));
        for (int i = 0; i < p.getContents().size(); i++) {
            p.getContents().get(i).setDisplayOrder(i + 1);
        }
        return toResponse(programRepository.save(p));
    }

    // ── DMS document linking ─────────────────────────────────

    @Audited(action = AuditAction.UPDATE, module = AuditModule.TRAINING, entityType = "TrainingProgram", entityIdArgIndex = 0)
    @Transactional
    public ProgramResponse linkDocument(Long programId, Long dmsDocumentId,
                                         String dmsDocNumber, String dmsDocVersion,
                                         String dmsDocTitle, Boolean triggerReview) {
        TrainingProgram p = findById(programId);
        boolean alreadyLinked = p.getDocumentLinks().stream()
                .anyMatch(l -> l.getDmsDocumentId().equals(dmsDocumentId));
        if (alreadyLinked) {
            throw AppException.conflict("Document " + dmsDocNumber + " is already linked to this program");
        }
        ProgramDocumentLink link = ProgramDocumentLink.builder()
                .program(p).dmsDocumentId(dmsDocumentId)
                .dmsDocNumber(dmsDocNumber).dmsDocVersion(dmsDocVersion).dmsDocTitle(dmsDocTitle)
                .triggerReviewOnUpdate(triggerReview != null ? triggerReview : true)
                .linkedBy(currentUsername())
                .build();
        p.getDocumentLinks().add(link);
        return toResponse(programRepository.save(p));
    }

    @Audited(action = AuditAction.UPDATE, module = AuditModule.TRAINING, entityType = "TrainingProgram", entityIdArgIndex = 0)
    @Transactional
    public ProgramResponse unlinkDocument(Long programId, Long linkId) {
        TrainingProgram p = findById(programId);
        p.getDocumentLinks().removeIf(l -> l.getId().equals(linkId));
        return toResponse(programRepository.save(p));
    }

    // ── Delete ───────────────────────────────────────────────

    @Audited(action = AuditAction.DELETE, module = AuditModule.TRAINING, entityType = "TrainingProgram", entityIdArgIndex = 0, captureNewValue = false, description = "TrainingProgram record deleted")
    @Transactional
    public void delete(Long id) {
        TrainingProgram p = findById(id);
        AuditContextHolder.set(AuditContext.builder()
                .oldValue(auditSerializer.serialize(toResponse(p)))
                .build());
        long activeEnrollments = enrollmentRepository
                .countByProgram_IdAndStatusAndIsDeletedFalse(id,
                        com.qms.module.lms.enums.EnrollmentStatus.IN_PROGRESS);
        if (activeEnrollments > 0) {
            throw AppException.badRequest(
                    "Cannot delete a program with " + activeEnrollments + " in-progress enrollments");
        }
        p.setIsDeleted(true);
        programRepository.save(p);
        log.info("Training program deleted: {}", p.getCode());
    }

    // ── Helpers ──────────────────────────────────────────────

    TrainingProgram findById(Long id) {
        return programRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> AppException.notFound("Training Program", id));
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "SYSTEM";
    }

    ProgramResponse toResponse(TrainingProgram p) {
        long totalEnrollments = enrollmentRepository.countByProgram_IdAndStatusAndIsDeletedFalse(
                p.getId() != null ? p.getId() : 0L, com.qms.module.lms.enums.EnrollmentStatus.COMPLETED)
                + enrollmentRepository.countByProgram_IdAndStatusAndIsDeletedFalse(
                p.getId() != null ? p.getId() : 0L, com.qms.module.lms.enums.EnrollmentStatus.ENROLLED);
        long completed = enrollmentRepository.countByProgram_IdAndStatusAndIsDeletedFalse(
                p.getId() != null ? p.getId() : 0L, com.qms.module.lms.enums.EnrollmentStatus.COMPLETED);
        Double rate = p.getId() != null
                ? enrollmentRepository.getComplianceRateForProgram(p.getId()) : null;

        List<ProgramResponse.ContentItemSummary> contentSummaries = p.getContents().stream()
                .map(c -> ProgramResponse.ContentItemSummary.builder()
                        .id(c.getId()).contentType(c.getContentType().name()).title(c.getTitle())
                        .displayOrder(c.getDisplayOrder()).isRequired(c.getIsRequired())
                        .durationMinutes(c.getDurationMinutes()).dmsDocNumber(c.getDmsDocNumber())
                        .build())
                .toList();

        List<ProgramResponse.DocumentLinkSummary> linkSummaries = p.getDocumentLinks().stream()
                .map(l -> ProgramResponse.DocumentLinkSummary.builder()
                        .id(l.getId()).dmsDocumentId(l.getDmsDocumentId())
                        .dmsDocNumber(l.getDmsDocNumber()).dmsDocVersion(l.getDmsDocVersion())
                        .dmsDocTitle(l.getDmsDocTitle()).triggerReviewOnUpdate(l.getTriggerReviewOnUpdate())
                        .build())
                .toList();

        List<ProgramResponse.SessionSummary> sessionSummaries = p.getSessions().stream()
                .map(s -> ProgramResponse.SessionSummary.builder()
                        .id(s.getId())
                        .sessionDate(s.getSessionDate() != null ? s.getSessionDate().toString() : null)
                        .sessionEndDate(s.getSessionEndDate() != null ? s.getSessionEndDate().toString() : null)
                        .venue(s.getVenue()).meetingLink(s.getMeetingLink())
                        .trainerName(s.getTrainerName())
                        .status(s.getStatus() != null ? s.getStatus().name() : null)
                        .presentCount(attendanceRepository.countPresentBySession(
                                s.getId() != null ? s.getId() : 0L))
                        .build())
                .toList();

        return ProgramResponse.builder()
                .id(p.getId()).code(p.getCode()).title(p.getTitle())
                .description(p.getDescription())
                .trainingType(p.getTrainingType()).trainingSubType(p.getTrainingSubType())
                .category(p.getCategory()).department(p.getDepartment())
                .departments(p.getDepartments()).tags(p.getTags()).status(p.getStatus())
                .isMandatory(p.getIsMandatory())
                .trainerId(p.getTrainerId()).trainerName(p.getTrainerName())
                .vendorName(p.getVendorName())
                .coordinatorId(p.getCoordinatorId()).coordinatorName(p.getCoordinatorName())
                .location(p.getLocation()).conferenceLink(p.getConferenceLink())
                .examEnabled(p.getExamEnabled()).assessmentRequired(p.getAssessmentRequired())
                .estimatedDurationMinutes(p.getEstimatedDurationMinutes())
                .certificateValidityYears(p.getCertificateValidityYears())
                .completionDeadlineDays(p.getCompletionDeadlineDays())
                .passScore(p.getPassScore()).maxAttempts(p.getMaxAttempts())
                .rejectionReason(p.getRejectionReason())
                .ownerName(p.getOwnerName())
                .totalEnrollments(totalEnrollments).completedEnrollments(completed)
                .complianceRate(rate).contents(contentSummaries).documentLinks(linkSummaries)
                .sessions(sessionSummaries)
                .hasAssessment(p.getAssessment() != null)
                .createdAt(p.getCreatedAt()).updatedAt(p.getUpdatedAt()).createdBy(p.getCreatedBy())
                .build();
    }
}
