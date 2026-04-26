package com.qms.module.qms.incident.service;

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
import com.qms.module.qms.incident.dto.request.IncidentRequest;
import com.qms.module.qms.incident.dto.response.IncidentResponse;
import com.qms.module.qms.incident.entity.Incident;
import com.qms.module.qms.incident.repository.IncidentRepository;
import com.qms.module.qms.incident.repository.IncidentSpecification;
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
public class IncidentService {

    private static final String TABLE = "qms_incident";

    private final IncidentRepository    incidentRepository;
    private final QmsWorkflowEngine     workflowEngine;
    private final RecordNumberGenerator recordNumberGenerator;
    private final QmsRecordMapper       recordMapper;
    private final AuditValueSerializer  auditSerializer;

    public PageResponse<IncidentResponse> search(QmsStatus status, Priority priority,
                                                  String severity, String incidentType,
                                                  Long assignedTo, String department,
                                                  String search, int page, int size) {

        Specification<Incident> spec = IncidentSpecification.filter(status,priority,severity,incidentType,assignedTo,department,search);
        var pageResult = incidentRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));

        return PageResponse.of(pageResult.map(this::toResponse));
//        return PageResponse.of(
//                incidentRepository.search(status, priority, severity, incidentType,
//                        assignedTo, department, search,
//                        PageRequest.of(page, size, Sort.by("createdAt").descending()))
//                        .map(this::toResponse));
    }

    public IncidentResponse getById(Long id) {
        return toResponse(findById(id));
    }

    @Audited(action = AuditAction.CREATE, module = AuditModule.INCIDENT, entityType = "Incident", description = "Incident record created")
    @Transactional
    public IncidentResponse create(IncidentRequest req) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "SYSTEM";

        Incident i = Incident.builder().build();
        i.setRecordNumber(recordNumberGenerator.generate(QmsRecordType.INCIDENT, TABLE));
        i.setStatus(QmsStatus.DRAFT);
        i.setRaisedByName(username);
        recordMapper.applyRequest(req, i);
        applyFields(req, i);

        Incident saved = incidentRepository.save(i);
        log.info("Incident created: {} by {}", saved.getRecordNumber(), username);
        return toResponse(saved);
    }

    @Audited(action = AuditAction.UPDATE, module = AuditModule.INCIDENT, entityType = "Incident", entityIdArgIndex = 0)
    @Transactional
    public IncidentResponse update(Long id, IncidentRequest req) {
        Incident i = findById(id);
        AuditContextHolder.set(AuditContext.builder()
                .oldValue(auditSerializer.serialize(toResponse(i)))
                .build());
        if (i.isTerminal()) throw AppException.badRequest("Cannot update a " + i.getStatus() + " Incident");
        recordMapper.applyRequest(req, i);
        applyFields(req, i);
        return toResponse(incidentRepository.save(i));
    }

    @Audited(action = AuditAction.UPDATE, module = AuditModule.INCIDENT, entityType = "Incident", entityIdArgIndex = 0)
    @Transactional
    public IncidentResponse transition(Long id, WorkflowRequest req) {
        Incident i = findById(id);
        workflowEngine.transition(i, req.getTargetStatus(), req.getComment());
        return toResponse(incidentRepository.save(i));
    }

    @Audited(action = AuditAction.SUBMIT, module = AuditModule.INCIDENT, entityType = "Incident", entityIdArgIndex = 0)
    @Transactional
    public IncidentResponse submit(Long id, String comment) {
        Incident i = findById(id);
        workflowEngine.submit(i, comment);
        return toResponse(incidentRepository.save(i));
    }

    @Audited(action = AuditAction.APPROVE, module = AuditModule.INCIDENT, entityType = "Incident", entityIdArgIndex = 0)
    @Transactional
    public IncidentResponse approve(Long id, String comment) {
        Incident i = findById(id);
        workflowEngine.approve(i, comment);
        return toResponse(incidentRepository.save(i));
    }

    @Audited(action = AuditAction.REJECT, module = AuditModule.INCIDENT, entityType = "Incident", entityIdArgIndex = 0)
    @Transactional
    public IncidentResponse reject(Long id, String comment) {
        Incident i = findById(id);
        workflowEngine.reject(i, comment);
        return toResponse(incidentRepository.save(i));
    }

    @Audited(action = AuditAction.CLOSE, module = AuditModule.INCIDENT, entityType = "Incident", entityIdArgIndex = 0)
    @Transactional
    public IncidentResponse close(Long id, String comment) {
        Incident i = findById(id);
        workflowEngine.close(i, comment);
        return toResponse(incidentRepository.save(i));
    }

    @Audited(action = AuditAction.CANCEL, module = AuditModule.INCIDENT, entityType = "Incident", entityIdArgIndex = 0)
    @Transactional
    public IncidentResponse cancel(Long id, String comment) {
        Incident i = findById(id);
        workflowEngine.cancel(i, comment);
        return toResponse(incidentRepository.save(i));
    }

    @Audited(action = AuditAction.REOPEN, module = AuditModule.INCIDENT, entityType = "Incident", entityIdArgIndex = 0)
    @Transactional
    public IncidentResponse reopen(Long id, String comment) {
        Incident i = findById(id);
        workflowEngine.reopen(i, comment);
        return toResponse(incidentRepository.save(i));
    }

    @Audited(action = AuditAction.DELETE, module = AuditModule.INCIDENT, entityType = "Incident", entityIdArgIndex = 0, captureNewValue = false, description = "Incident record deleted")
    @Transactional
    public void delete(Long id) {
        Incident i = findById(id);
        AuditContextHolder.set(AuditContext.builder()
                .oldValue(auditSerializer.serialize(toResponse(i)))
                .build());
        i.setIsDeleted(true);
        incidentRepository.save(i);
    }

    private Incident findById(Long id) {
        return incidentRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> AppException.notFound("Incident", id));
    }

    private void applyFields(IncidentRequest req, Incident i) {
        if (req.getIncidentType()        != null) i.setIncidentType(req.getIncidentType());
        if (req.getSeverity()            != null) i.setSeverity(req.getSeverity());
        if (req.getLocation()            != null) i.setLocation(req.getLocation());
        if (req.getOccurrenceDate()      != null) i.setOccurrenceDate(req.getOccurrenceDate());
        if (req.getReportedBy()          != null) i.setReportedBy(req.getReportedBy());
        if (req.getImmediateAction()     != null) i.setImmediateAction(req.getImmediateAction());
        if (req.getInvestigationDetails() != null) i.setInvestigationDetails(req.getInvestigationDetails());
        if (req.getCapaReference()       != null) i.setCapaReference(req.getCapaReference());
        if (req.getInjuryInvolved()      != null) i.setInjuryInvolved(req.getInjuryInvolved());
        if (req.getInjuryDetails()       != null) i.setInjuryDetails(req.getInjuryDetails());
        if (req.getIncidentSubType()    != null) i.setIncidentSubType(req.getIncidentSubType());
        if (req.getRetestingRequired()  != null) i.setRetestingRequired(req.getRetestingRequired());
        if (req.getDeviationRequired()  != null) i.setDeviationRequired(req.getDeviationRequired());
    }

    private IncidentResponse toResponse(Incident i) {
        IncidentResponse r = new IncidentResponse();
        recordMapper.applyResponse(i, r);
        r.setIncidentType(i.getIncidentType());
        r.setSeverity(i.getSeverity());
        r.setLocation(i.getLocation());
        r.setOccurrenceDate(i.getOccurrenceDate());
        r.setReportedBy(i.getReportedBy());
        r.setImmediateAction(i.getImmediateAction());
        r.setInvestigationDetails(i.getInvestigationDetails());
        r.setCapaReference(i.getCapaReference());
        r.setInjuryInvolved(i.getInjuryInvolved());
        r.setInjuryDetails(i.getInjuryDetails());
        r.setIncidentSubType(i.getIncidentSubType());
        r.setRetestingRequired(i.getRetestingRequired());
        r.setDeviationRequired(i.getDeviationRequired());
        return r;
    }
}
