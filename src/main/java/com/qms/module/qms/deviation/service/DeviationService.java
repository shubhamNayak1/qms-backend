package com.qms.module.qms.deviation.service;

import com.qms.common.enums.Priority;
import com.qms.common.enums.QmsRecordType;
import com.qms.common.enums.QmsStatus;
import com.qms.common.exception.AppException;
import com.qms.common.response.PageResponse;
import com.qms.module.qms.capa.entity.Capa;
import com.qms.module.qms.capa.repository.CapaSpecification;
import com.qms.module.qms.common.dto.request.WorkflowRequest;
import com.qms.module.qms.common.service.QmsRecordMapper;
import com.qms.module.qms.common.service.RecordNumberGenerator;
import com.qms.module.qms.common.workflow.QmsWorkflowEngine;
import com.qms.module.qms.deviation.dto.request.DeviationRequest;
import com.qms.module.qms.deviation.dto.response.DeviationResponse;
import com.qms.module.qms.deviation.entity.Deviation;
import com.qms.module.qms.deviation.repository.DeviationRepository;
import com.qms.module.qms.deviation.repository.DeviationSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeviationService {

    private static final String TABLE = "qms_deviation";

    private final DeviationRepository    deviationRepository;
    private final QmsWorkflowEngine      workflowEngine;
    private final RecordNumberGenerator  recordNumberGenerator;
    private final QmsRecordMapper        recordMapper;
    private final AuditValueSerializer   auditSerializer;

    public PageResponse<DeviationResponse> search(QmsStatus status, Priority priority,
                                                   Long assignedTo, String department,
                                                   String deviationType, String search,
                                                   int page, int size) {

        Specification<Deviation> spec = DeviationSpecification.filter(status,priority,assignedTo,department,deviationType,search);
        var pageResult = deviationRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));

        return PageResponse.of(pageResult.map(this::toResponse));
//        return PageResponse.of(
//                deviationRepository.search(status, priority, assignedTo, department, deviationType, search,
//                        PageRequest.of(page, size, Sort.by("createdAt").descending()))
//                        .map(this::toResponse));
    }

    public DeviationResponse getById(Long id) {
        return toResponse(findById(id));
    }

    @Audited(action = AuditAction.CREATE, module = AuditModule.DEVIATION, entityType = "Deviation", description = "Deviation record created")
    @Transactional
    public DeviationResponse create(DeviationRequest req) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "SYSTEM";

        Deviation d = Deviation.builder().build();
        d.setRecordNumber(recordNumberGenerator.generate(QmsRecordType.DEVIATION, TABLE));
        d.setStatus(QmsStatus.DRAFT);
        d.setRaisedByName(username);
        recordMapper.applyRequest(req, d);
        applyFields(req, d);

        Deviation saved = deviationRepository.save(d);
        log.info("Deviation created: {} by {}", saved.getRecordNumber(), username);
        return toResponse(saved);
    }

    @Audited(action = AuditAction.UPDATE, module = AuditModule.DEVIATION, entityType = "Deviation", entityIdArgIndex = 0)
    @Transactional
    public DeviationResponse update(Long id, DeviationRequest req) {
        Deviation d = findById(id);
        AuditContextHolder.set(AuditContext.builder()
                .oldValue(auditSerializer.serialize(toResponse(d)))
                .build());
        if (d.isTerminal()) throw AppException.badRequest("Cannot update a " + d.getStatus() + " Deviation");
        recordMapper.applyRequest(req, d);
        applyFields(req, d);
        return toResponse(deviationRepository.save(d));
    }

    @Audited(action = AuditAction.UPDATE, module = AuditModule.DEVIATION, entityType = "Deviation", entityIdArgIndex = 0)
    @Transactional
    public DeviationResponse transition(Long id, WorkflowRequest req) {
        Deviation d = findById(id);
        workflowEngine.transition(d, req.getTargetStatus(), req.getComment());
        return toResponse(deviationRepository.save(d));
    }

    @Audited(action = AuditAction.SUBMIT, module = AuditModule.DEVIATION, entityType = "Deviation", entityIdArgIndex = 0)
    @Transactional
    public DeviationResponse submit(Long id, String comment) {
        Deviation d = findById(id);
        workflowEngine.submit(d, comment);
        return toResponse(deviationRepository.save(d));
    }

    @Audited(action = AuditAction.APPROVE, module = AuditModule.DEVIATION, entityType = "Deviation", entityIdArgIndex = 0)
    @Transactional
    public DeviationResponse approve(Long id, String comment) {
        Deviation d = findById(id);
        workflowEngine.approve(d, comment);
        return toResponse(deviationRepository.save(d));
    }

    @Audited(action = AuditAction.REJECT, module = AuditModule.DEVIATION, entityType = "Deviation", entityIdArgIndex = 0)
    @Transactional
    public DeviationResponse reject(Long id, String comment) {
        Deviation d = findById(id);
        workflowEngine.reject(d, comment);
        return toResponse(deviationRepository.save(d));
    }

    @Audited(action = AuditAction.CLOSE, module = AuditModule.DEVIATION, entityType = "Deviation", entityIdArgIndex = 0)
    @Transactional
    public DeviationResponse close(Long id, String comment) {
        Deviation d = findById(id);
        workflowEngine.close(d, comment);
        return toResponse(deviationRepository.save(d));
    }

    @Audited(action = AuditAction.CANCEL, module = AuditModule.DEVIATION, entityType = "Deviation", entityIdArgIndex = 0)
    @Transactional
    public DeviationResponse cancel(Long id, String comment) {
        Deviation d = findById(id);
        workflowEngine.cancel(d, comment);
        return toResponse(deviationRepository.save(d));
    }

    @Audited(action = AuditAction.REOPEN, module = AuditModule.DEVIATION, entityType = "Deviation", entityIdArgIndex = 0)
    @Transactional
    public DeviationResponse reopen(Long id, String comment) {
        Deviation d = findById(id);
        workflowEngine.reopen(d, comment);
        return toResponse(deviationRepository.save(d));
    }

    @Audited(action = AuditAction.DELETE, module = AuditModule.DEVIATION, entityType = "Deviation", entityIdArgIndex = 0, captureNewValue = false, description = "Deviation record deleted")
    @Transactional
    public void delete(Long id) {
        Deviation d = findById(id);
        AuditContextHolder.set(AuditContext.builder()
                .oldValue(auditSerializer.serialize(toResponse(d)))
                .build());
        d.setIsDeleted(true);
        deviationRepository.save(d);
    }

    private Deviation findById(Long id) {
        return deviationRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> AppException.notFound("Deviation", id));
    }

    private void applyFields(DeviationRequest req, Deviation d) {
        if (req.getDeviationType()       != null) d.setDeviationType(req.getDeviationType());
        if (req.getProductBatch()        != null) d.setProductBatch(req.getProductBatch());
        if (req.getProcessArea()         != null) d.setProcessArea(req.getProcessArea());
        if (req.getImpactAssessment()    != null) d.setImpactAssessment(req.getImpactAssessment());
        if (req.getCapaRequired()        != null) d.setCapaRequired(req.getCapaRequired());
        if (req.getCapaReference()       != null) d.setCapaReference(req.getCapaReference());
        if (req.getRegulatoryReportable() != null) d.setRegulatoryReportable(req.getRegulatoryReportable());
    }

    private DeviationResponse toResponse(Deviation d) {
        DeviationResponse r = new DeviationResponse();
        recordMapper.applyResponse(d, r);
        r.setDeviationType(d.getDeviationType());
        r.setProductBatch(d.getProductBatch());
        r.setProcessArea(d.getProcessArea());
        r.setImpactAssessment(d.getImpactAssessment());
        r.setCapaRequired(d.getCapaRequired());
        r.setCapaReference(d.getCapaReference());
        r.setRegulatoryReportable(d.getRegulatoryReportable());
        return r;
    }
}
