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
import com.qms.module.lms.repository.TrainingProgramRepository;
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

    private final TrainingProgramRepository programRepository;
    private final EnrollmentRepository      enrollmentRepository;
    private final AuditValueSerializer      auditSerializer;

    // ── Queries ──────────────────────────────────────────────

    public PageResponse<ProgramResponse> search(ProgramStatus status, String category,
                                                 String department, Boolean mandatory,
                                                 String search, int page, int size) {
        // Pre-build the wildcard pattern in Java so the repository query receives a typed
        // varchar value — avoids Hibernate 6 binding null as bytea in PostgreSQL.
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

    // ── Commands ─────────────────────────────────────────────

    @Audited(action = AuditAction.CREATE, module = AuditModule.TRAINING, entityType = "TrainingProgram", description = "TrainingProgram record created")
    @Transactional
    public ProgramResponse create(ProgramRequest req) {
        if (programRepository.existsByCodeAndIsDeletedFalse(req.getCode())) {
            throw AppException.conflict("A program with code '" + req.getCode() + "' already exists");
        }
        String username = currentUsername();
        TrainingProgram program = TrainingProgram.builder()
                .code(req.getCode().toUpperCase())
                .title(req.getTitle())
                .description(req.getDescription())
                .category(req.getCategory())
                .department(req.getDepartment())
                .tags(req.getTags())
                .status(ProgramStatus.DRAFT)
                .isMandatory(req.getIsMandatory() != null ? req.getIsMandatory() : false)
                .estimatedDurationMinutes(req.getEstimatedDurationMinutes())
                .certificateValidityYears(req.getCertificateValidityYears())
                .completionDeadlineDays(req.getCompletionDeadlineDays())
                .assessmentRequired(req.getAssessmentRequired() != null
                        ? req.getAssessmentRequired() : false)
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
        if (req.getCategory()                   != null) p.setCategory(req.getCategory());
        if (req.getDepartment()                 != null) p.setDepartment(req.getDepartment());
        if (req.getTags()                       != null) p.setTags(req.getTags());
        if (req.getIsMandatory()                != null) p.setIsMandatory(req.getIsMandatory());
        if (req.getEstimatedDurationMinutes()   != null) p.setEstimatedDurationMinutes(req.getEstimatedDurationMinutes());
        if (req.getCertificateValidityYears()   != null) p.setCertificateValidityYears(req.getCertificateValidityYears());
        if (req.getCompletionDeadlineDays()     != null) p.setCompletionDeadlineDays(req.getCompletionDeadlineDays());
        if (req.getAssessmentRequired()         != null) p.setAssessmentRequired(req.getAssessmentRequired());
        if (req.getPassScore()                  != null) p.setPassScore(req.getPassScore());
        if (req.getMaxAttempts()                != null) p.setMaxAttempts(req.getMaxAttempts());
        if (req.getOwnerId()                    != null) p.setOwnerId(req.getOwnerId());
        return toResponse(programRepository.save(p));
    }

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
        log.info("Training program published: {}", p.getCode());
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

    @Audited(action = AuditAction.UPDATE, module = AuditModule.TRAINING, entityType = "TrainingProgram", entityIdArgIndex = 0)
    @Transactional
    public ProgramResponse addContent(Long programId, String title, ContentType type,
                                       String contentUrl, Long dmsDocumentId, String dmsDocNumber,
                                       String dmsDocVersion, String inlineContent,
                                       Integer durationMinutes, Boolean required) {
        TrainingProgram p = findById(programId);
        if (p.getStatus() == ProgramStatus.ARCHIVED) {
            throw AppException.badRequest("Cannot add content to an archived program");
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
        // Re-number display order
        for (int i = 0; i < p.getContents().size(); i++) {
            p.getContents().get(i).setDisplayOrder(i + 1);
        }
        return toResponse(programRepository.save(p));
    }

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

        return ProgramResponse.builder()
                .id(p.getId()).code(p.getCode()).title(p.getTitle())
                .description(p.getDescription()).category(p.getCategory())
                .department(p.getDepartment()).tags(p.getTags()).status(p.getStatus())
                .isMandatory(p.getIsMandatory())
                .estimatedDurationMinutes(p.getEstimatedDurationMinutes())
                .certificateValidityYears(p.getCertificateValidityYears())
                .completionDeadlineDays(p.getCompletionDeadlineDays())
                .assessmentRequired(p.getAssessmentRequired())
                .passScore(p.getPassScore()).maxAttempts(p.getMaxAttempts())
                .ownerName(p.getOwnerName())
                .totalEnrollments(totalEnrollments).completedEnrollments(completed)
                .complianceRate(rate).contents(contentSummaries).documentLinks(linkSummaries)
                .hasAssessment(p.getAssessment() != null)
                .createdAt(p.getCreatedAt()).updatedAt(p.getUpdatedAt()).createdBy(p.getCreatedBy())
                .build();
    }
}
