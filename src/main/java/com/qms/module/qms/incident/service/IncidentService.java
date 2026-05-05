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
import com.qms.module.qms.capa.dto.response.CapaResponse;
import com.qms.module.qms.capa.service.CapaService;
import com.qms.module.qms.deviation.entity.Deviation;
import com.qms.module.qms.deviation.repository.DeviationRepository;
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
    private final DeviationRepository   deviationRepository;
    private final CapaService           capaService;
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

    /**
     * Cross-module handoff: creates a Deviation record bound to this Incident
     * and moves the Incident to {@code DEVIATION_SPAWNED} (terminal). Used at
     * the General + Deviation Required path on the Kedar-sir flow chart —
     * QA confirms during Assessment by QA, the Deviation # is generated,
     * and the Incident terminates here while the Deviation continues
     * independently with {@code parent_incident_id} pointing back.
     *
     * Idempotent: if the Incident already has a spawned Deviation, returns
     * its number rather than creating a second one.
     */
    @Audited(action = AuditAction.SUBMIT, module = AuditModule.INCIDENT,
             entityType = "Incident", entityIdArgIndex = 0,
             description = "Incident spawned a Deviation cross-link")
    @Transactional
    public IncidentResponse spawnDeviation(Long id, String comment) {
        Incident i = findById(id);

        // Guard rails — only General Incidents flagged deviation_required can spawn.
        if (!"GENERAL".equalsIgnoreCase(i.getIncidentSubType())) {
            throw AppException.badRequest(
                    "Only General Incidents can spawn a Deviation; this is a "
                    + i.getIncidentSubType() + " Incident.");
        }
        if (!Boolean.TRUE.equals(i.getDeviationRequired())) {
            throw AppException.badRequest(
                    "deviation_required must be TRUE before a Deviation can be spawned. " +
                    "Have the HOD or QA Reviewer set the flag at the assessment stage first.");
        }
        if (i.getStatus() != QmsStatus.PENDING_QA_REVIEW) {
            throw AppException.badRequest(
                    "A Deviation can only be spawned while the Incident is at PENDING_QA_REVIEW. " +
                    "Current status: " + i.getStatus());
        }
        if (i.getSpawnedDeviationId() != null) {
            // Idempotent — if we already spawned, return the existing link.
            log.info("Incident {} already spawned Deviation {} ({}); returning existing link.",
                    i.getRecordNumber(), i.getSpawnedDeviationId(), i.getSpawnedDeviationNumber());
            return toResponse(i);
        }

        AuditContextHolder.set(AuditContext.builder()
                .oldValue(auditSerializer.serialize(toResponse(i)))
                .build());

        // Build the Deviation. We copy the operational context (department,
        // batch, area, immediate action) so the Initiator on the Deviation
        // side doesn't have to retype context the QA Reviewer just typed.
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = (auth != null && auth.isAuthenticated()) ? auth.getName() : "SYSTEM";

        Deviation d = Deviation.builder().build();
        d.setRecordType(QmsRecordType.DEVIATION);
        d.setRecordNumber(recordNumberGenerator.generate(QmsRecordType.DEVIATION, "qms_deviation"));
        d.setStatus(QmsStatus.DRAFT);
        d.setTitle("[From Incident " + i.getRecordNumber() + "] " + i.getTitle());
        d.setDescription(i.getDescription());
        d.setPriority(i.getPriority());
        d.setDepartmentId(i.getDepartmentId());
        d.setDepartment(i.getDepartment());
        d.setRaisedById(i.getRaisedById());
        d.setRaisedByName(username);
        d.setParentIncidentId(i.getId());
        d.setProductBatch(null);                      // Incident has no batch — left for Deviation HOD to fill
        d.setProcessArea(i.getLocation());            // Incident.location maps onto Deviation.processArea
        d.setDeviationType("Unplanned");
        d.setRegulatoryReportable(false);

        Deviation savedDev = deviationRepository.save(d);
        log.info("Spawned Deviation {} from Incident {}",
                savedDev.getRecordNumber(), i.getRecordNumber());

        // Stamp the cross-link back on the Incident, then transition it to
        // DEVIATION_SPAWNED via the engine so the audit trail records the
        // status change with a proper comment.
        i.setSpawnedDeviationId(savedDev.getId());
        i.setSpawnedDeviationNumber(savedDev.getRecordNumber());
        workflowEngine.transition(i, QmsStatus.DEVIATION_SPAWNED,
                (comment == null || comment.isBlank())
                        ? "Spawned Deviation " + savedDev.getRecordNumber()
                        : comment + " — spawned " + savedDev.getRecordNumber());

        Incident savedInc = incidentRepository.save(i);
        return toResponse(savedInc);
    }

    /**
     * Cross-module CAPA spawn — called from this Incident's HOD Assessment
     * stage when {@code capa_required = true}. Idempotent: if the Incident
     * already carries a {@code linked_capa_number}, returns the existing CAPA.
     */
    @Audited(action = AuditAction.SUBMIT, module = AuditModule.INCIDENT,
             entityType = "Incident", entityIdArgIndex = 0,
             description = "Incident spawned a CAPA cross-link")
    @Transactional
    public CapaResponse spawnCapa(Long id, String preliminaryInvestigation) {
        Incident i = findById(id);

        if (i.getLinkedCapaNumber() != null && !i.getLinkedCapaNumber().isBlank()) {
            log.info("Incident {} already linked to CAPA {}; skipping spawn.",
                    i.getRecordNumber(), i.getLinkedCapaNumber());
            // Caller must look up the existing CAPA themselves; we return the
            // current incident response in the controller layer.
            return null;
        }

        AuditContextHolder.set(AuditContext.builder()
                .oldValue(auditSerializer.serialize(toResponse(i)))
                .build());

        CapaResponse capa = capaService.spawnFromParent(i, preliminaryInvestigation);
        i.setCapaRequired(true);
        i.setLinkedCapaNumber(capa.getRecordNumber());
        i.setCapaReference(capa.getRecordNumber());
        incidentRepository.save(i);

        log.info("Spawned CAPA {} from Incident {}", capa.getRecordNumber(), i.getRecordNumber());
        return capa;
    }

    private Incident findById(Long id) {
        return incidentRepository.findById(id)
                .orElseThrow(() -> AppException.notFound("Incident", id));
    }

    private void applyFields(IncidentRequest req, Incident i) {
        if (req.getIncidentType()             != null) i.setIncidentType(req.getIncidentType());
        if (req.getSeverity()                 != null) i.setSeverity(req.getSeverity());
        if (req.getLocation()                 != null) i.setLocation(req.getLocation());
        if (req.getOccurrenceDate()           != null) i.setOccurrenceDate(req.getOccurrenceDate());
        if (req.getReportedBy()               != null) i.setReportedBy(req.getReportedBy());
        if (req.getImmediateAction()          != null) i.setImmediateAction(req.getImmediateAction());
        if (req.getInvestigationDetails()     != null) i.setInvestigationDetails(req.getInvestigationDetails());
        if (req.getCapaReference()            != null) i.setCapaReference(req.getCapaReference());
        if (req.getCapaRequired()             != null) i.setCapaRequired(req.getCapaRequired());
        if (req.getLinkedCapaNumber()         != null) i.setLinkedCapaNumber(req.getLinkedCapaNumber());
        if (req.getInjuryInvolved()           != null) i.setInjuryInvolved(req.getInjuryInvolved());
        if (req.getInjuryDetails()            != null) i.setInjuryDetails(req.getInjuryDetails());
        if (req.getIncidentSubType()          != null) i.setIncidentSubType(req.getIncidentSubType());
        if (req.getRetestingRequired()        != null) i.setRetestingRequired(req.getRetestingRequired());
        if (req.getDeviationRequired()        != null) i.setDeviationRequired(req.getDeviationRequired());
        if (req.getSiteHeadRequired()         != null) i.setSiteHeadRequired(req.getSiteHeadRequired());
        if (req.getAbnormalityRemedialAction()!= null) i.setAbnormalityRemedialAction(req.getAbnormalityRemedialAction());
        if (req.getVerificationNarrative()    != null) i.setVerificationNarrative(req.getVerificationNarrative());
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
        r.setCapaRequired(i.getCapaRequired());
        r.setLinkedCapaNumber(i.getLinkedCapaNumber());
        r.setInjuryInvolved(i.getInjuryInvolved());
        r.setInjuryDetails(i.getInjuryDetails());
        r.setIncidentSubType(i.getIncidentSubType());
        r.setRetestingRequired(i.getRetestingRequired());
        r.setDeviationRequired(i.getDeviationRequired());
        r.setSiteHeadRequired(i.getSiteHeadRequired());
        r.setAbnormalityRemedialAction(i.getAbnormalityRemedialAction());
        r.setSpawnedDeviationId(i.getSpawnedDeviationId());
        r.setSpawnedDeviationNumber(i.getSpawnedDeviationNumber());
        r.setVerificationNarrative(i.getVerificationNarrative());
        return r;
    }
}
