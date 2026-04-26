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
import com.qms.module.qms.common.dto.request.WorkflowRequest;
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

    private final CapaRepository         capaRepository;
    private final QmsWorkflowEngine      workflowEngine;
    private final RecordNumberGenerator  recordNumberGenerator;
    private final QmsRecordMapper        recordMapper;
    private final AuditValueSerializer   auditSerializer;

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
        return toResponse(capaRepository.save(capa));
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

    @Transactional
    public CapaResponse recordEffectiveness(Long id, EffectivenessRequest req) {
        Capa capa = findById(id);
        if (capa.getStatus() != QmsStatus.CLOSED) {
            throw AppException.badRequest("Effectiveness can only be recorded for CLOSED CAPAs");
        }
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
        return capaRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> AppException.notFound("CAPA", id));
    }

    private void applyCapaFields(CapaRequest req, Capa capa) {
        if (req.getSource()                 != null) capa.setSource(req.getSource());
        if (req.getCapaType()               != null) capa.setCapaType(req.getCapaType());
        if (req.getPreventiveAction()       != null) capa.setPreventiveAction(req.getPreventiveAction());
        if (req.getEffectivenessCheckDate() != null) capa.setEffectivenessCheckDate(req.getEffectivenessCheckDate());
        if (req.getLinkedDeviationNumber()  != null) capa.setLinkedDeviationNumber(req.getLinkedDeviationNumber());
    }

    private CapaResponse toResponse(Capa capa) {
        CapaResponse r = new CapaResponse();
        recordMapper.applyResponse(capa, r);
        r.setSource(capa.getSource());
        r.setCapaType(capa.getCapaType());
        r.setPreventiveAction(capa.getPreventiveAction());
        r.setEffectivenessCheckDate(capa.getEffectivenessCheckDate());
        r.setEffectivenessResult(capa.getEffectivenessResult());
        r.setIsEffective(capa.getIsEffective());
        r.setLinkedDeviationNumber(capa.getLinkedDeviationNumber());
        return r;
    }
}
