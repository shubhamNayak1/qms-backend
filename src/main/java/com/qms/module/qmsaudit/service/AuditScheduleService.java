package com.qms.module.qmsaudit.service;

import com.qms.common.enums.AuditAction;
import com.qms.common.enums.AuditModule;
import com.qms.common.enums.QmsRecordType;
import com.qms.common.exception.AppException;
import com.qms.common.response.PageResponse;
import com.qms.module.audit.annotation.Audited;
import com.qms.module.qms.common.service.RecordNumberGenerator;
import com.qms.module.qmsaudit.dto.request.CreateAuditScheduleRequest;
import com.qms.module.qmsaudit.dto.request.UpdateAuditScheduleRequest;
import com.qms.module.qmsaudit.dto.response.AuditScheduleResponse;
import com.qms.module.qmsaudit.entity.AuditSchedule;
import com.qms.module.qmsaudit.enums.AuditScheduleStatus;
import com.qms.module.qmsaudit.enums.AuditType;
import com.qms.module.qmsaudit.repository.AuditScheduleRepository;
import com.qms.module.qmsaudit.repository.AuditScheduleSpecification;
import com.qms.module.user.entity.User;
import com.qms.module.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditScheduleService {

    private static final String TABLE = "qms_audit_schedule";

    private final AuditScheduleRepository repository;
    private final RecordNumberGenerator   recordNumberGenerator;
    private final UserRepository          userRepository;

    // ── Create ────────────────────────────────────────────────

    @Audited(action = AuditAction.CREATE, module = AuditModule.QMS_AUDIT,
             entityType = "AuditSchedule", description = "QMS audit scheduled")
    @Transactional
    public AuditScheduleResponse create(CreateAuditScheduleRequest req) {
        String auditorName = resolveAuditorName(req.getLeadAuditorId(), req.getLeadAuditorName());

        AuditSchedule audit = AuditSchedule.builder()
                .recordNumber(recordNumberGenerator.generate(QmsRecordType.AUDIT_SCHEDULE, TABLE))
                .title(req.getTitle())
                .auditType(req.getAuditType())
                .scope(req.getScope())
                .leadAuditorId(req.getLeadAuditorId())
                .leadAuditorName(auditorName)
                .scheduledDate(req.getScheduledDate())
                .completedDate(req.getCompletedDate())
                .findings(req.getFindings())
                .observations(req.getObservations())
                .status(AuditScheduleStatus.PLANNED)
                .build();

        audit = repository.save(audit);
        log.info("Audit scheduled: {} ({})", audit.getRecordNumber(), audit.getAuditType());
        return toResponse(audit);
    }

    // ── Update ────────────────────────────────────────────────

    @Audited(action = AuditAction.UPDATE, module = AuditModule.QMS_AUDIT,
             entityType = "AuditSchedule", entityIdArgIndex = 0,
             captureOldValue = true, description = "QMS audit details updated")
    @Transactional
    public AuditScheduleResponse update(Long id, UpdateAuditScheduleRequest req) {
        AuditSchedule audit = findById(id);

        if (req.getTitle()          != null) audit.setTitle(req.getTitle());
        if (req.getAuditType()      != null) audit.setAuditType(req.getAuditType());
        if (req.getScope()          != null) audit.setScope(req.getScope());
        if (req.getScheduledDate()  != null) audit.setScheduledDate(req.getScheduledDate());
        if (req.getCompletedDate()  != null) audit.setCompletedDate(req.getCompletedDate());
        if (req.getFindings()       != null) audit.setFindings(req.getFindings());
        if (req.getObservations()   != null) audit.setObservations(req.getObservations());

        if (req.getLeadAuditorId() != null || req.getLeadAuditorName() != null) {
            audit.setLeadAuditorId(req.getLeadAuditorId());
            audit.setLeadAuditorName(
                    resolveAuditorName(req.getLeadAuditorId(), req.getLeadAuditorName()));
        }

        return toResponse(repository.save(audit));
    }

    // ── Workflow transitions ──────────────────────────────────

    @Audited(action = AuditAction.UPDATE, module = AuditModule.QMS_AUDIT,
             entityType = "AuditSchedule", entityIdArgIndex = 0,
             description = "Audit started — status changed to IN_PROGRESS")
    @Transactional
    public AuditScheduleResponse start(Long id) {
        AuditSchedule audit = findById(id);
        if (audit.getStatus() != AuditScheduleStatus.PLANNED) {
            throw AppException.badRequest("Only PLANNED audits can be started. Current status: " + audit.getStatus());
        }
        audit.setStatus(AuditScheduleStatus.IN_PROGRESS);
        return toResponse(repository.save(audit));
    }

    @Audited(action = AuditAction.CLOSE, module = AuditModule.QMS_AUDIT,
             entityType = "AuditSchedule", entityIdArgIndex = 0,
             description = "Audit completed")
    @Transactional
    public AuditScheduleResponse complete(Long id, String findings, String observations) {
        AuditSchedule audit = findById(id);
        if (audit.getStatus() == AuditScheduleStatus.COMPLETED) {
            throw AppException.badRequest("Audit is already completed.");
        }
        if (audit.getStatus() == AuditScheduleStatus.CANCELLED) {
            throw AppException.badRequest("Cannot complete a cancelled audit.");
        }
        audit.setStatus(AuditScheduleStatus.COMPLETED);
        audit.setCompletedDate(LocalDate.now());
        if (findings    != null) audit.setFindings(findings);
        if (observations != null) audit.setObservations(observations);
        return toResponse(repository.save(audit));
    }

    @Audited(action = AuditAction.CANCEL, module = AuditModule.QMS_AUDIT,
             entityType = "AuditSchedule", entityIdArgIndex = 0,
             description = "Audit cancelled")
    @Transactional
    public AuditScheduleResponse cancel(Long id) {
        AuditSchedule audit = findById(id);
        if (audit.getStatus() == AuditScheduleStatus.COMPLETED) {
            throw AppException.badRequest("Cannot cancel a completed audit.");
        }
        if (audit.getStatus() == AuditScheduleStatus.CANCELLED) {
            throw AppException.badRequest("Audit is already cancelled.");
        }
        audit.setStatus(AuditScheduleStatus.CANCELLED);
        return toResponse(repository.save(audit));
    }

    // ── Delete (soft) ─────────────────────────────────────────

    @Audited(action = AuditAction.DELETE, module = AuditModule.QMS_AUDIT,
             entityType = "AuditSchedule", entityIdArgIndex = 0,
             captureNewValue = false, description = "QMS audit deleted")
    @Transactional
    public void delete(Long id) {
        AuditSchedule audit = findById(id);
        audit.setIsDeleted(true);
        repository.save(audit);
        log.info("Audit {} soft-deleted", audit.getRecordNumber());
    }

    // ── Queries ───────────────────────────────────────────────

    public AuditScheduleResponse getById(Long id) {
        return toResponse(findById(id));
    }

    public PageResponse<AuditScheduleResponse> search(
            AuditType type, AuditScheduleStatus status,
            LocalDate from, LocalDate to, String search,
            int page, int size) {

        Specification<AuditSchedule> spec =
                AuditScheduleSpecification.filter(type, status, from, to, search);

        return PageResponse.of(
                repository.findAll(spec,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "scheduledDate")))
                        .map(this::toResponse));
    }

    // ── Helpers ───────────────────────────────────────────────

    private AuditSchedule findById(Long id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> AppException.notFound("Audit Schedule", id));
    }

    private String resolveAuditorName(Long auditorId, String fallbackName) {
        if (auditorId != null) {
            return userRepository.findById(auditorId)
                    .map(User::getFullName)
                    .orElse(fallbackName);
        }
        return fallbackName;
    }

    private AuditScheduleResponse toResponse(AuditSchedule a) {
        boolean overdue = a.getStatus() == AuditScheduleStatus.PLANNED
                && a.getScheduledDate() != null
                && a.getScheduledDate().isBefore(LocalDate.now());

        return AuditScheduleResponse.builder()
                .id(a.getId())
                .recordNumber(a.getRecordNumber())
                .title(a.getTitle())
                .auditType(a.getAuditType())
                .scope(a.getScope())
                .leadAuditorId(a.getLeadAuditorId())
                .leadAuditorName(a.getLeadAuditorName())
                .scheduledDate(a.getScheduledDate())
                .completedDate(a.getCompletedDate())
                .findings(a.getFindings())
                .observations(a.getObservations())
                .status(a.getStatus())
                .overdue(overdue)
                .createdBy(a.getCreatedBy())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }
}
