package com.qms.module.qms.capa.service;

import com.qms.common.enums.AuditAction;
import com.qms.common.enums.AuditModule;
import com.qms.common.enums.Priority;
import com.qms.common.enums.QmsRecordType;
import com.qms.common.enums.QmsStatus;
import com.qms.common.exception.AppException;
import com.qms.common.response.PageResponse;
import com.qms.module.audit.annotation.Audited;
import com.qms.module.audit.context.AuditContext;
import com.qms.module.audit.context.AuditContextHolder;
import com.qms.module.audit.entity.AuditLog;
import com.qms.module.audit.repository.AuditLogSpecification;
import com.qms.module.audit.service.AuditValueSerializer;
import com.qms.module.qms.capa.dto.request.CapaRequest;
import com.qms.module.qms.capa.dto.request.EffectivenessRequest;
import com.qms.module.qms.capa.dto.response.CapaResponse;
import com.qms.module.qms.capa.entity.Capa;
import com.qms.module.qms.capa.repository.CapaRepository;
import com.qms.module.qms.capa.repository.CapaSpecification;
import com.qms.module.qms.common.entity.QmsRecord;
import com.qms.module.qms.common.dto.request.WorkflowRequest;
import com.qms.module.qms.common.service.QmsCapaAssessmentService;
import com.qms.module.qms.common.service.QmsRecordMapper;
import com.qms.module.qms.common.service.RecordNumberGenerator;
import com.qms.module.qms.common.workflow.QmsWorkflowEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CapaService {

    private static final String TABLE = "qms_capa";

    private final CapaRepository           capaRepository;
    private final QmsWorkflowEngine        workflowEngine;
    private final RecordNumberGenerator    recordNumberGenerator;
    private final QmsRecordMapper          recordMapper;
    private final AuditValueSerializer     auditSerializer;
    private final QmsCapaAssessmentService assessmentService;

    // ── Queries ──────────────────────────────────────────────

    public PageResponse<CapaResponse> search(QmsStatus status, Priority priority,
                                              Long assignedTo, String department,
                                              String source, String search,
                                              int page, int size) {
        Specification<Capa> spec = CapaSpecification.filter(status,priority,assignedTo,department,source,search);
        var pageResult = capaRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));

        return PageResponse.of(pageResult.map(this::toResponse));

//        return PageResponse.of(
//                capaRepository.search(status, priority, assignedTo, department, source, search,
//                        PageRequest.of(page, size, Sort.by("createdAt").descending()))
//                        .map(this::toResponse));
    }

    public CapaResponse getById(Long id) {
        return toResponse(findById(id));
    }

    public CapaResponse getByRecordNumber(String recordNumber) {
        return toResponse(capaRepository
                .findByRecordNumberAndIsDeletedFalse(recordNumber)
                .orElseThrow(() -> AppException.notFound("CAPA", recordNumber)));
    }

    // ── Commands ─────────────────────────────────────────────

    @Audited(action = AuditAction.CREATE, module = AuditModule.CAPA,
             entityType = "Capa", description = "CAPA record created")
    @Transactional
    public CapaResponse create(CapaRequest req) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "SYSTEM";

        Capa capa = Capa.builder().build();
        capa.setRecordNumber(recordNumberGenerator.generate(QmsRecordType.CAPA, TABLE));
        capa.setStatus(QmsStatus.DRAFT);
        capa.setRaisedByName(username);
        recordMapper.applyRequest(req, capa);
        applyCapaFields(req, capa);

        Capa saved = capaRepository.save(capa);
        log.info("CAPA created: {} by {}", saved.getRecordNumber(), username);
        return toResponse(saved);
    }

    @Audited(action = AuditAction.UPDATE, module = AuditModule.CAPA,
             entityType = "Capa", entityIdArgIndex = 0)
    @Transactional
    public CapaResponse update(Long id, CapaRequest req) {
        Capa capa = findById(id);
        AuditContextHolder.set(AuditContext.builder()
                .oldValue(auditSerializer.serialize(toResponse(capa)))
                .build());
        if (capa.isTerminal()) {
            throw AppException.badRequest("Cannot update a " + capa.getStatus() + " CAPA");
        }
        recordMapper.applyRequest(req, capa);
        applyCapaFields(req, capa);
        return toResponse(capaRepository.save(capa));
    }

    @Audited(action = AuditAction.UPDATE, module = AuditModule.CAPA,
             entityType = "Capa", entityIdArgIndex = 0,
             description = "CAPA workflow transition")
    @Transactional
    public CapaResponse transition(Long id, WorkflowRequest req) {
        Capa capa = findById(id);
        workflowEngine.transition(capa, req.getTargetStatus(), req.getComment());
        return toResponse(capaRepository.save(capa));
    }

    @Audited(action = AuditAction.SUBMIT, module = AuditModule.CAPA,
             entityType = "Capa", entityIdArgIndex = 0)
    @Transactional
    public CapaResponse submit(Long id, String comment) {
        Capa capa = findById(id);
        workflowEngine.submit(capa, comment);
        return toResponse(capaRepository.save(capa));
    }

    @Audited(action = AuditAction.APPROVE, module = AuditModule.CAPA,
             entityType = "Capa", entityIdArgIndex = 0)
    @Transactional
    public CapaResponse approve(Long id, String comment) {
        Capa capa = findById(id);
        workflowEngine.approve(capa, comment);
        return toResponse(capaRepository.save(capa));
    }

    @Audited(action = AuditAction.REJECT, module = AuditModule.CAPA,
             entityType = "Capa", entityIdArgIndex = 0)
    @Transactional
    public CapaResponse reject(Long id, String comment) {
        Capa capa = findById(id);
        workflowEngine.reject(capa, comment);
        return toResponse(capaRepository.save(capa));
    }

    @Audited(action = AuditAction.CLOSE, module = AuditModule.CAPA,
             entityType = "Capa", entityIdArgIndex = 0)
    @Transactional
    public CapaResponse close(Long id, String comment) {
        Capa capa = findById(id);
        workflowEngine.close(capa, comment);
        Capa saved = capaRepository.save(capa);
        // Seed the effectiveness-assessment lifecycle if Head QA configured it.
        // The seed method is idempotent; calling it always is safe.
        if (saved.getAssessmentCount() != null && saved.getAssessmentCount() > 0) {
            assessmentService.seed(saved.getId(),
                    saved.getAssessmentFrequency(),
                    saved.getAssessmentCount());
        } else {
            saved.setAssessmentSummaryStatus("NOT_REQUIRED");
            saved = capaRepository.save(saved);
        }
        return toResponse(saved);
    }

    @Audited(action = AuditAction.CANCEL, module = AuditModule.CAPA,
             entityType = "Capa", entityIdArgIndex = 0)
    @Transactional
    public CapaResponse cancel(Long id, String comment) {
        Capa capa = findById(id);
        workflowEngine.cancel(capa, comment);
        return toResponse(capaRepository.save(capa));
    }

    @Audited(action = AuditAction.REOPEN, module = AuditModule.CAPA,
             entityType = "Capa", entityIdArgIndex = 0)
    @Transactional
    public CapaResponse reopen(Long id, String comment) {
        Capa capa = findById(id);
        workflowEngine.reopen(capa, comment);
        return toResponse(capaRepository.save(capa));
    }

    /**
     * Cross-module CAPA spawn — called by Incident / Deviation / Change
     * Control / Market Complaint services when their HOD or QA Reviewer
     * decides "CAPA Required = Yes" and the parent record needs a fresh
     * CAPA cross-link.
     *
     * Builds a new CAPA at DRAFT with the polymorphic parent fields filled.
     * The caller is responsible for stamping the new CAPA's record number
     * back onto the parent (e.g. {@code linked_capa_number} or
     * {@code capa_reference}) inside their own transaction.
     *
     * Idempotency is handled by the caller — they should check whether
     * their parent already carries a CAPA reference before invoking this.
     */
    @Audited(action = AuditAction.CREATE, module = AuditModule.CAPA,
             entityType = "Capa",
             description = "CAPA spawned from a parent record cross-link")
    @Transactional
    public CapaResponse spawnFromParent(QmsRecord parent, String preliminaryInvestigation) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = (auth != null && auth.isAuthenticated()) ? auth.getName() : "SYSTEM";

        Capa capa = Capa.builder().build();
        capa.setRecordNumber(recordNumberGenerator.generate(QmsRecordType.CAPA, TABLE));
        capa.setStatus(QmsStatus.DRAFT);
        capa.setRecordType(QmsRecordType.CAPA);
        capa.setTitle("[From " + parent.getRecordType() + " " + parent.getRecordNumber() + "] "
                      + parent.getTitle());
        capa.setDescription(preliminaryInvestigation == null
                ? parent.getDescription() : preliminaryInvestigation);
        capa.setPriority(parent.getPriority());
        capa.setDepartmentId(parent.getDepartmentId());
        capa.setDepartment(parent.getDepartment());
        capa.setRaisedById(parent.getRaisedById());
        capa.setRaisedByName(username);
        // Assign to the parent's raiser by default so the spawned CAPA shows
        // in their bell — they need to flesh out the proposed CAPA at
        // PENDING_HOD next. Without this, the legacy assignment-based
        // notification path leaves DRAFT records invisible.
        capa.setAssignedToId(parent.getRaisedById());
        capa.setAssignedToName(parent.getRaisedByName());

        capa.setCapaOrigin("EXISTING");
        capa.setParentRecordType(parent.getRecordType().name());
        capa.setParentRecordId(parent.getId());
        capa.setParentRecordNumber(parent.getRecordNumber());

        // Source mirrors the parent's module so reports group cleanly.
        switch (parent.getRecordType()) {
            case INCIDENT         -> capa.setSource("Incident");
            case DEVIATION        -> capa.setSource("Deviation");
            case CHANGE_CONTROL   -> capa.setSource("Change Control");
            case MARKET_COMPLAINT -> capa.setSource("Market Complaint");
            default               -> capa.setSource("Internal");
        }
        capa.setCapaType("Corrective");

        // Legacy compat — populate the deviation-specific column when
        // appropriate so any old report/UI that still keys on it keeps working.
        if (parent.getRecordType() == QmsRecordType.DEVIATION) {
            capa.setLinkedDeviationNumber(parent.getRecordNumber());
        }

        Capa saved = capaRepository.save(capa);
        log.info("Spawned CAPA {} from {} {}",
                saved.getRecordNumber(), parent.getRecordType(), parent.getRecordNumber());
        return toResponse(saved);
    }

    @Audited(action = AuditAction.UPDATE, module = AuditModule.CAPA,
             entityType = "Capa", entityIdArgIndex = 0,
             description = "CAPA effectiveness verdict recorded (legacy single-shot path)")
    @Transactional
    public CapaResponse recordEffectiveness(Long id, EffectivenessRequest req) {
        Capa capa = findById(id);
        if (capa.getStatus() != QmsStatus.CLOSED) {
            throw AppException.badRequest("Effectiveness can only be recorded for CLOSED CAPAs");
        }
        AuditContextHolder.set(AuditContext.builder()
                .oldValue(auditSerializer.serialize(toResponse(capa)))
                .build());
        capa.setIsEffective(req.getIsEffective());
        capa.setEffectivenessResult(req.getEffectivenessResult());
        return toResponse(capaRepository.save(capa));
    }

    @Audited(action = AuditAction.DELETE, module = AuditModule.CAPA,
             entityType = "Capa", entityIdArgIndex = 0, captureNewValue = false,
             description = "CAPA record deleted")
    @Transactional
    public void delete(Long id) {
        Capa capa = findById(id);
        AuditContextHolder.set(AuditContext.builder()
                .oldValue(auditSerializer.serialize(toResponse(capa)))
                .build());
        capa.setIsDeleted(true);
        capaRepository.save(capa);
        log.info("CAPA soft-deleted: {}", capa.getRecordNumber());
    }

    public List<CapaResponse> getPendingEffectivenessChecks() {
        return capaRepository
                .findPendingEffectivenessChecks(LocalDate.now(), LocalDate.now().plusDays(30))
                .stream().map(this::toResponse).toList();
    }

    // ── Helpers ──────────────────────────────────────────────

    private Capa findById(Long id) {
        return capaRepository.findById(id)
                .orElseThrow(() -> AppException.notFound("CAPA", id));
    }

    private void applyCapaFields(CapaRequest req, Capa capa) {
        if (req.getCapaOrigin()              != null) capa.setCapaOrigin(req.getCapaOrigin());
        if (req.getParentRecordType()        != null) capa.setParentRecordType(req.getParentRecordType());
        if (req.getParentRecordId()          != null) capa.setParentRecordId(req.getParentRecordId());
        if (req.getParentRecordNumber()      != null) capa.setParentRecordNumber(req.getParentRecordNumber());
        if (req.getSource()                  != null) capa.setSource(req.getSource());
        if (req.getCapaType()                != null) capa.setCapaType(req.getCapaType());
        if (req.getPreventiveAction()        != null) capa.setPreventiveAction(req.getPreventiveAction());
        if (req.getSiteHeadRequired()        != null) capa.setSiteHeadRequired(req.getSiteHeadRequired());
        if (req.getVerificationReviewComment()!= null) capa.setVerificationReviewComment(req.getVerificationReviewComment());
        if (req.getEffectivenessCheckDate()  != null) capa.setEffectivenessCheckDate(req.getEffectivenessCheckDate());
        if (req.getAssessmentFrequency()     != null) capa.setAssessmentFrequency(req.getAssessmentFrequency());
        if (req.getAssessmentCount()         != null) capa.setAssessmentCount(req.getAssessmentCount());
        if (req.getLinkedDeviationNumber()   != null) capa.setLinkedDeviationNumber(req.getLinkedDeviationNumber());
    }

    private CapaResponse toResponse(Capa capa) {
        CapaResponse r = new CapaResponse();
        recordMapper.applyResponse(capa, r);
        r.setCapaOrigin(capa.getCapaOrigin());
        r.setParentRecordType(capa.getParentRecordType());
        r.setParentRecordId(capa.getParentRecordId());
        r.setParentRecordNumber(capa.getParentRecordNumber());
        r.setSource(capa.getSource());
        r.setCapaType(capa.getCapaType());
        r.setPreventiveAction(capa.getPreventiveAction());
        r.setSiteHeadRequired(capa.getSiteHeadRequired());
        r.setVerificationReviewComment(capa.getVerificationReviewComment());
        r.setEffectivenessCheckDate(capa.getEffectivenessCheckDate());
        r.setEffectivenessResult(capa.getEffectivenessResult());
        r.setIsEffective(capa.getIsEffective());
        r.setAssessmentFrequency(capa.getAssessmentFrequency());
        r.setAssessmentCount(capa.getAssessmentCount());
        r.setAssessmentSummaryStatus(capa.getAssessmentSummaryStatus());
        r.setLinkedDeviationNumber(capa.getLinkedDeviationNumber());
        return r;
    }
}
