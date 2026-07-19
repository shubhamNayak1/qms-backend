package com.qms.module.qms.changecontrol.service;

import com.qms.common.enums.Priority;
import com.qms.common.enums.QmsRecordType;
import com.qms.common.enums.QmsStatus;
import com.qms.common.exception.AppException;
import com.qms.common.response.PageResponse;
import com.qms.module.dms.repository.DocumentRepository;
import com.qms.module.qms.changecontrol.dto.request.ChangeControlRequest;
import com.qms.module.qms.changecontrol.dto.response.ChangeControlResponse;
import com.qms.module.qms.changecontrol.entity.ChangeControl;
import com.qms.module.qms.changecontrol.repository.ChangeControlRepository;
import com.qms.module.qms.changecontrol.repository.ChangeControlSpecification;
import com.qms.module.qms.capa.dto.response.CapaResponse;
import com.qms.module.qms.capa.service.CapaService;
import com.qms.module.qms.common.dto.request.WorkflowRequest;
import com.qms.module.qms.common.service.QmsRecordMapper;
import com.qms.module.qms.common.service.RecordNumberGenerator;
import com.qms.module.qms.common.workflow.QmsWorkflowEngine;
import com.qms.module.user.entity.Role;
import com.qms.module.user.repository.RoleSpecification;
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
public class ChangeControlService {

    private static final String TABLE = "qms_change_control";

    private final ChangeControlRepository changeControlRepository;
    private final CapaService             capaService;
    private final QmsWorkflowEngine       workflowEngine;
    private final RecordNumberGenerator   recordNumberGenerator;
    private final QmsRecordMapper         recordMapper;
    private final AuditValueSerializer    auditSerializer;
    // Round-N (2026-07-04) tester CC-Point-2 · Issue 7 — row-level ACL.
    private final com.qms.module.org.service.OrgSecurityService orgSecurity;
    /**
     * Used to resolve the Initiator's attachment to its DMS title/version
     * when the {@code initialAttachmentRef} parses as a numeric document id.
     * Best-effort: a parse failure or missing doc simply leaves the DMS
     * fields blank on the response.
     */
    private final DocumentRepository      documentRepository;
    /**
     * Round-2 A1 — used to resolve a local-file attachment when
     * {@code initialAttachmentRef} starts with "QMS-ATT-". Best-effort:
     * a missing row leaves the local-file metadata blank on the response.
     */
    private final com.qms.module.qms.common.repository.QmsRecordAttachmentRepository
            recordAttachmentRepository;
    /** Round-3 R8 — used to assert at least one line item exists before submit. */
    private final com.qms.module.qms.common.repository.QmsLineItemRepository
            lineItemRepository;

    public PageResponse<ChangeControlResponse> search(QmsStatus status, Priority priority,
                                                       String changeType, String riskLevel,
                                                       Long assignedTo, String department,
                                                       String search, int page, int size) {
        Specification<ChangeControl> spec = ChangeControlSpecification.filter(status,priority,changeType,riskLevel,assignedTo,department,search);
        // Round-N (2026-07-04) tester CC-Point-2 · Issue 7 — restrict
        // the list to records the current user has authority on. See
        // QmsVisibilitySpecification for the rule set.
        spec = spec.and(com.qms.module.qms.common.repository.QmsVisibilitySpecification
                .visibleToCurrentUser(orgSecurity,
                        com.qms.common.enums.QmsRecordType.CHANGE_CONTROL));
        var pageResult = changeControlRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));

        return PageResponse.of(pageResult.map(this::toResponse));

//        return PageResponse.of(
//                changeControlRepository.search(status, priority, changeType, riskLevel,
//                        assignedTo, department, search,
//                        PageRequest.of(page, size, Sort.by("createdAt").descending()))
//                        .map(this::toResponse));
    }

    public ChangeControlResponse getById(Long id) {
        return toResponse(findById(id));
    }

    @Audited(action = AuditAction.CREATE, module = AuditModule.CHANGE_CONTROL, entityType = "ChangeControl", description = "ChangeControl record created")
    @Transactional
    public ChangeControlResponse create(ChangeControlRequest req) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "SYSTEM";

        ChangeControl cc = ChangeControl.builder().build();
        cc.setRecordNumber(recordNumberGenerator.generate(QmsRecordType.CHANGE_CONTROL, TABLE));
        cc.setStatus(QmsStatus.DRAFT);
        cc.setRaisedByName(username);
        recordMapper.applyRequest(req, cc);
        applyFields(req, cc);

        ChangeControl saved = changeControlRepository.save(cc);
        log.info("Change Control created: {} by {}", saved.getRecordNumber(), username);
        return toResponse(saved);
    }

    @Audited(action = AuditAction.UPDATE, module = AuditModule.CHANGE_CONTROL, entityType = "ChangeControl", entityIdArgIndex = 0)
    @Transactional
    public ChangeControlResponse update(Long id, ChangeControlRequest req) {
        ChangeControl cc = findById(id);
        AuditContextHolder.set(AuditContext.builder()
                .oldValue(auditSerializer.serialize(toResponse(cc)))
                .build());
        if (cc.isTerminal()) throw AppException.badRequest("Cannot update a " + cc.getStatus() + " Change Control");
        recordMapper.applyRequest(req, cc);
        applyFields(req, cc);
        return toResponse(changeControlRepository.save(cc));
    }

    @Audited(action = AuditAction.UPDATE, module = AuditModule.CHANGE_CONTROL, entityType = "ChangeControl", entityIdArgIndex = 0)
    @Transactional
    public ChangeControlResponse transition(Long id, WorkflowRequest req) {
        ChangeControl cc = findById(id);
        workflowEngine.transition(cc, req.getTargetStatus(), req.getComment());
        return toResponse(changeControlRepository.save(cc));
    }

    @Audited(action = AuditAction.SUBMIT, module = AuditModule.CHANGE_CONTROL, entityType = "ChangeControl", entityIdArgIndex = 0)
    @Transactional
    public ChangeControlResponse submit(Long id, String comment) {
        ChangeControl cc = findById(id);
        // Round-3 R8: a Change Control must carry at least one line item. The
        // Initiator can delete every line item during DRAFT editing — submit
        // is the gate that catches it.
        long itemCount = lineItemRepository.countByRecordTypeAndRecordIdAndIsDeletedFalse(
                com.qms.common.enums.QmsRecordType.CHANGE_CONTROL, id);
        if (itemCount == 0) {
            throw com.qms.common.exception.AppException.badRequest(
                "At least one line item is required to submit a Change Control. " +
                "Add an Existing System / Proposed System row before submitting.");
        }
        workflowEngine.submit(cc, comment);
        return toResponse(changeControlRepository.save(cc));
    }

    @Audited(action = AuditAction.APPROVE, module = AuditModule.CHANGE_CONTROL, entityType = "ChangeControl", entityIdArgIndex = 0)
    @Transactional
    public ChangeControlResponse approve(Long id, String comment) {
        ChangeControl cc = findById(id);
        workflowEngine.approve(cc, comment);
        return toResponse(changeControlRepository.save(cc));
    }

    @Audited(action = AuditAction.REJECT, module = AuditModule.CHANGE_CONTROL, entityType = "ChangeControl", entityIdArgIndex = 0)
    @Transactional
    public ChangeControlResponse reject(Long id, String comment) {
        ChangeControl cc = findById(id);
        workflowEngine.reject(cc, comment);
        return toResponse(changeControlRepository.save(cc));
    }

    @Audited(action = AuditAction.CLOSE, module = AuditModule.CHANGE_CONTROL, entityType = "ChangeControl", entityIdArgIndex = 0)
    @Transactional
    public ChangeControlResponse close(Long id, String comment) {
        ChangeControl cc = findById(id);
        workflowEngine.close(cc, comment);
        return toResponse(changeControlRepository.save(cc));
    }

    @Audited(action = AuditAction.CANCEL, module = AuditModule.CHANGE_CONTROL, entityType = "ChangeControl", entityIdArgIndex = 0)
    @Transactional
    public ChangeControlResponse cancel(Long id, String comment) {
        ChangeControl cc = findById(id);
        workflowEngine.cancel(cc, comment);
        return toResponse(changeControlRepository.save(cc));
    }

    @Audited(action = AuditAction.REOPEN, module = AuditModule.CHANGE_CONTROL, entityType = "ChangeControl", entityIdArgIndex = 0)
    @Transactional
    public ChangeControlResponse reopen(Long id, String comment) {
        ChangeControl cc = findById(id);
        workflowEngine.reopen(cc, comment);
        return toResponse(changeControlRepository.save(cc));
    }

    @Audited(action = AuditAction.DELETE, module = AuditModule.CHANGE_CONTROL, entityType = "ChangeControl", entityIdArgIndex = 0, captureNewValue = false, description = "ChangeControl record deleted")
    @Transactional
    public void delete(Long id) {
        ChangeControl cc = findById(id);
        AuditContextHolder.set(AuditContext.builder()
                .oldValue(auditSerializer.serialize(toResponse(cc)))
                .build());
        cc.setIsDeleted(true);
        changeControlRepository.save(cc);
    }

    /**
     * Cross-module CAPA spawn — called when this Change Control needs a
     * CAPA cross-link. Stamps the new CAPA's record number on this CC's
     * {@code linked_capa_number}. Idempotent.
     */
    @Audited(action = AuditAction.SUBMIT, module = AuditModule.CHANGE_CONTROL,
             entityType = "ChangeControl", entityIdArgIndex = 0,
             description = "Change Control spawned a CAPA cross-link")
    @Transactional
    public ChangeControlResponse spawnCapa(Long id, String preliminaryInvestigation) {
        ChangeControl cc = findById(id);
        if (cc.getLinkedCapaNumber() != null && !cc.getLinkedCapaNumber().isBlank()) {
            log.info("Change Control {} already linked to CAPA {}; skipping spawn.",
                    cc.getRecordNumber(), cc.getLinkedCapaNumber());
            return toResponse(cc);
        }
        AuditContextHolder.set(AuditContext.builder()
                .oldValue(auditSerializer.serialize(toResponse(cc)))
                .build());

        CapaResponse capa = capaService.spawnFromParent(cc, preliminaryInvestigation);
        cc.setLinkedCapaNumber(capa.getRecordNumber());
        log.info("Spawned CAPA {} from Change Control {}",
                capa.getRecordNumber(), cc.getRecordNumber());
        return toResponse(changeControlRepository.save(cc));
    }

    private ChangeControl findById(Long id) {
        return changeControlRepository.findById(id)
                .orElseThrow(() -> AppException.notFound("Change Control", id));
    }

    private void applyFields(ChangeControlRequest req, ChangeControl cc) {
        if (req.getChangeType()                    != null) cc.setChangeType(req.getChangeType());
        if (req.getChangeReason()                  != null) cc.setChangeReason(req.getChangeReason());
        if (req.getProductMaterial()               != null) cc.setProductMaterial(req.getProductMaterial());
        if (req.getProductMaterialCode()           != null) cc.setProductMaterialCode(req.getProductMaterialCode());
        if (req.getMarketDetails()                 != null) cc.setMarketDetails(req.getMarketDetails());
        if (req.getPreRemark()                     != null) cc.setPreRemark(req.getPreRemark());
        if (req.getInitialAttachmentRef()          != null) cc.setInitialAttachmentRef(req.getInitialAttachmentRef());
        if (req.getLinkedCapaNumber()              != null) cc.setLinkedCapaNumber(req.getLinkedCapaNumber());
        if (req.getRiskLevel()                     != null) cc.setRiskLevel(req.getRiskLevel());
        if (req.getRiskAssessment()                != null) cc.setRiskAssessment(req.getRiskAssessment());
        if (req.getImplementationPlan()            != null) cc.setImplementationPlan(req.getImplementationPlan());
        if (req.getImplementationDate()            != null) cc.setImplementationDate(req.getImplementationDate());
        if (req.getValidationRequired()            != null) cc.setValidationRequired(req.getValidationRequired());
        if (req.getValidationDetails()             != null) cc.setValidationDetails(req.getValidationDetails());
        if (req.getValidationCompletionDate()      != null) cc.setValidationCompletionDate(req.getValidationCompletionDate());
        if (req.getRegulatorySubmissionRequired()  != null) cc.setRegulatorySubmissionRequired(req.getRegulatorySubmissionRequired());
        if (req.getRegulatorySubmissionReference() != null) cc.setRegulatorySubmissionReference(req.getRegulatorySubmissionReference());
        // Batch B S3 / S4
        if (req.getRegulatorySubmissionCountry()   != null) cc.setRegulatorySubmissionCountry(req.getRegulatorySubmissionCountry());
        if (req.getQaEvaluationRemark()            != null) cc.setQaEvaluationRemark(req.getQaEvaluationRemark());
        if (req.getRollbackPlan()                  != null) cc.setRollbackPlan(req.getRollbackPlan());
        if (req.getSiteHeadRequired()              != null) cc.setSiteHeadRequired(req.getSiteHeadRequired());
        if (req.getCustomerCommentRequired()       != null) cc.setCustomerCommentRequired(req.getCustomerCommentRequired());
        if (req.getCustomerComment()               != null) cc.setCustomerComment(req.getCustomerComment());

        // Round-N (2026-07-04) tester CC-Point-2 · Issues 1 + 2.
        if (req.getImpactOnQualification()         != null) cc.setImpactOnQualification(req.getImpactOnQualification());
        if (req.getImpactOnDocumentation()         != null) cc.setImpactOnDocumentation(req.getImpactOnDocumentation());
        if (req.getImpactOnValidation()            != null) cc.setImpactOnValidation(req.getImpactOnValidation());
        if (req.getImpactOnMaterialSource()        != null) cc.setImpactOnMaterialSource(req.getImpactOnMaterialSource());
        if (req.getImpactRegulatoryAspects()       != null) cc.setImpactRegulatoryAspects(req.getImpactRegulatoryAspects());
        if (req.getImpactOnArtworkPack()           != null) cc.setImpactOnArtworkPack(req.getImpactOnArtworkPack());
        if (req.getImpactOther()                   != null) cc.setImpactOther(req.getImpactOther());
        if (req.getImpactOtherComment()            != null) cc.setImpactOtherComment(req.getImpactOtherComment());
        if (req.getInitialRiskAssessmentRequired() != null) cc.setInitialRiskAssessmentRequired(req.getInitialRiskAssessmentRequired());
        if (req.getInitialRiskAssessment()         != null) cc.setInitialRiskAssessment(req.getInitialRiskAssessment());
    }

    private ChangeControlResponse toResponse(ChangeControl cc) {
        ChangeControlResponse r = new ChangeControlResponse();
        recordMapper.applyResponse(cc, r);
        r.setChangeType(cc.getChangeType());
        r.setChangeReason(cc.getChangeReason());
        r.setProductMaterial(cc.getProductMaterial());
        r.setProductMaterialCode(cc.getProductMaterialCode());
        r.setMarketDetails(cc.getMarketDetails());
        r.setPreRemark(cc.getPreRemark());
        r.setInitialAttachmentRef(cc.getInitialAttachmentRef());
        // Round-2 A1: resolve attachment metadata for both stores.
        //   • "QMS-ATT-{id}"  → look up qms_record_attachments (local upload).
        //   • numeric id      → look up dms_documents (DMS picker).
        //   • anything else   → free-text reference, no resolution.
        String ref = cc.getInitialAttachmentRef();
        if (ref != null && !ref.isBlank()) {
            String trimmed = ref.trim();
            if (trimmed.startsWith("QMS-ATT-")) {
                try {
                    Long attId = Long.valueOf(trimmed.substring("QMS-ATT-".length()));
                    recordAttachmentRepository.findByIdAndIsDeletedFalse(attId).ifPresent(att -> {
                        // We surface the local-file metadata on the SAME
                        // dmsNumber/Title/Version response slots so the
                        // existing FE renderer just works — only the
                        // "version" field is omitted (no concept).
                        r.setInitialAttachmentDmsNumber(trimmed);
                        r.setInitialAttachmentDmsTitle(att.getFileName());
                    });
                } catch (NumberFormatException ignored) { /* malformed prefix */ }
            } else {
                try {
                    Long dmsId = Long.parseLong(trimmed);
                    documentRepository.findByIdAndIsDeletedFalse(dmsId).ifPresent(doc -> {
                        r.setInitialAttachmentDmsId(doc.getId());
                        r.setInitialAttachmentDmsNumber(doc.getDocNumber());
                        r.setInitialAttachmentDmsTitle(doc.getTitle());
                        r.setInitialAttachmentDmsVersion(doc.getVersion());
                    });
                } catch (NumberFormatException ignored) {
                    // Free-text reference — leave DMS fields blank.
                }
            }
        }
        r.setLinkedCapaNumber(cc.getLinkedCapaNumber());
        r.setRiskLevel(cc.getRiskLevel());
        r.setRiskAssessment(cc.getRiskAssessment());
        r.setImplementationPlan(cc.getImplementationPlan());
        r.setImplementationDate(cc.getImplementationDate());
        r.setValidationRequired(cc.getValidationRequired());
        r.setValidationDetails(cc.getValidationDetails());
        r.setValidationCompletionDate(cc.getValidationCompletionDate());
        r.setRegulatorySubmissionRequired(cc.getRegulatorySubmissionRequired());
        r.setRegulatorySubmissionReference(cc.getRegulatorySubmissionReference());
        // Batch B S3 / S4
        r.setRegulatorySubmissionCountry(cc.getRegulatorySubmissionCountry());
        r.setQaEvaluationRemark(cc.getQaEvaluationRemark());
        r.setRollbackPlan(cc.getRollbackPlan());
        r.setSiteHeadRequired(cc.getSiteHeadRequired());
        r.setCustomerCommentRequired(cc.getCustomerCommentRequired());
        r.setCustomerComment(cc.getCustomerComment());

        // Round-N (2026-07-04) tester CC-Point-2 · Issues 1 + 2.
        r.setImpactOnQualification(cc.getImpactOnQualification());
        r.setImpactOnDocumentation(cc.getImpactOnDocumentation());
        r.setImpactOnValidation(cc.getImpactOnValidation());
        r.setImpactOnMaterialSource(cc.getImpactOnMaterialSource());
        r.setImpactRegulatoryAspects(cc.getImpactRegulatoryAspects());
        r.setImpactOnArtworkPack(cc.getImpactOnArtworkPack());
        r.setImpactOther(cc.getImpactOther());
        r.setImpactOtherComment(cc.getImpactOtherComment());
        r.setInitialRiskAssessmentRequired(cc.getInitialRiskAssessmentRequired());
        r.setInitialRiskAssessment(cc.getInitialRiskAssessment());
        return r;
    }
}
