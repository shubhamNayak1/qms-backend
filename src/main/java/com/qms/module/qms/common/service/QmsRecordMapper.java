package com.qms.module.qms.common.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.qms.module.qms.common.dto.request.QmsBaseRequest;
import com.qms.module.qms.common.dto.response.QmsBaseResponse;
import com.qms.module.qms.common.entity.QmsRecord;
import com.qms.module.qms.common.workflow.StatusHistoryEntry;
import com.qms.module.qms.common.workflow.WorkflowTransition;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Reusable mapping helper shared by all QMS sub-module services.
 * Sub-module services call these methods to avoid duplicating
 * the same field-mapping code in every mapper class.
 */
@Component
public class QmsRecordMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    private static final TypeReference<List<StatusHistoryEntry>> HISTORY_TYPE =
            new TypeReference<>() {};

    /** Copy all shared QmsBaseRequest fields onto a QmsRecord entity. */
    public void applyRequest(QmsBaseRequest req, QmsRecord record) {
        if (req.getTitle()                       != null) record.setTitle(req.getTitle());
        if (req.getDescription()                 != null) record.setDescription(req.getDescription());
        if (req.getPriority()                    != null) record.setPriority(req.getPriority());
        if (req.getAssignedToId()                != null) record.setAssignedToId(req.getAssignedToId());
        if (req.getDepartment()                  != null) record.setDepartment(req.getDepartment());
        if (req.getDepartmentId()                != null) record.setDepartmentId(req.getDepartmentId());
        if (req.getDueDate()                     != null) record.setDueDate(req.getDueDate());
        if (req.getTargetCompletionDate()        != null) record.setTargetCompletionDate(req.getTargetCompletionDate());
        if (req.getRootCause()                   != null) record.setRootCause(req.getRootCause());
        if (req.getCorrectiveAction()            != null) record.setCorrectiveAction(req.getCorrectiveAction());
        if (req.getComments()                    != null) record.setComments(req.getComments());
        // Shared common fields lifted to QmsRecord (V19) — let HOD review,
        // RA evaluation, customer comment, verification stages all update
        // their dedicated fields via the per-module PUT endpoint.
        if (req.getInitialAssessment()           != null) record.setInitialAssessment(req.getInitialAssessment());
        if (req.getRiskAssessment()              != null) record.setRiskAssessment(req.getRiskAssessment());
        if (req.getCategory()                    != null) record.setCategory(req.getCategory());
        if (req.getCustomerCommunicationRequired() != null)
            record.setCustomerCommunicationRequired(req.getCustomerCommunicationRequired());
        if (req.getCustomerRepresentative()      != null) record.setCustomerRepresentative(req.getCustomerRepresentative());
        if (req.getCustomerComment()             != null) record.setCustomerComment(req.getCustomerComment());
        if (req.getVerificationActionTaken()       != null) record.setVerificationActionTaken(req.getVerificationActionTaken());
        if (req.getVerificationEffectiveOn()       != null) record.setVerificationEffectiveOn(req.getVerificationEffectiveOn());
        if (req.getVerificationDocumentsReissue()  != null) record.setVerificationDocumentsReissue(req.getVerificationDocumentsReissue());
        if (req.getVerificationOtherComments()     != null) record.setVerificationOtherComments(req.getVerificationOtherComments());
        if (req.getVerificationRegCommunication()  != null) record.setVerificationRegCommunication(req.getVerificationRegCommunication());
    }

    /** Copy all shared QmsRecord fields into a QmsBaseResponse DTO. */
    public void applyResponse(QmsRecord record, QmsBaseResponse response) {
        response.setId(record.getId());
        response.setRecordNumber(record.getRecordNumber());
        response.setRecordType(record.getRecordType());
        response.setTitle(record.getTitle());
        response.setDescription(record.getDescription());
        response.setStatus(record.getStatus());
        response.setPriority(record.getPriority());
        response.setAssignedToId(record.getAssignedToId());
        response.setAssignedToName(record.getAssignedToName());
        response.setRaisedById(record.getRaisedById());
        response.setRaisedByName(record.getRaisedByName());
        response.setDepartment(record.getDepartment());
        response.setDepartmentId(record.getDepartmentId());
        response.setCommentingDepartmentId(record.getCommentingDepartmentId());
        response.setDueDate(record.getDueDate());
        response.setClosedDate(record.getClosedDate());
        response.setTargetCompletionDate(record.getTargetCompletionDate());
        response.setApprovedById(record.getApprovedById());
        response.setApprovedByName(record.getApprovedByName());
        response.setApprovedAt(record.getApprovedAt());
        response.setApprovalComments(record.getApprovalComments());
        response.setRootCause(record.getRootCause());
        response.setCorrectiveAction(record.getCorrectiveAction());
        response.setComments(record.getComments());
        // Shared common fields lifted to QmsRecord (V19)
        response.setInitialAssessment(record.getInitialAssessment());
        response.setRiskAssessment(record.getRiskAssessment());
        response.setCategory(record.getCategory());
        response.setCustomerCommunicationRequired(record.getCustomerCommunicationRequired());
        response.setCustomerRepresentative(record.getCustomerRepresentative());
        response.setCustomerComment(record.getCustomerComment());
        response.setVerificationActionTaken(record.getVerificationActionTaken());
        response.setVerificationEffectiveOn(record.getVerificationEffectiveOn());
        response.setVerificationDocumentsReissue(record.getVerificationDocumentsReissue());
        response.setVerificationOtherComments(record.getVerificationOtherComments());
        response.setVerificationRegCommunication(record.getVerificationRegCommunication());
        response.setTargetDateExtensionDate(record.getTargetDateExtensionDate());
        response.setTargetDateExtensionReason(record.getTargetDateExtensionReason());
        response.setTargetDateExtensionStatus(record.getTargetDateExtensionStatus());
        response.setTargetDateExtensionRequestedById(record.getTargetDateExtensionRequestedById());
        response.setTargetDateExtensionRequestedAt(record.getTargetDateExtensionRequestedAt());
        response.setTargetDateExtensionDecidedById(record.getTargetDateExtensionDecidedById());
        response.setTargetDateExtensionDecidedAt(record.getTargetDateExtensionDecidedAt());
        response.setOverdue(record.isOverdue());
        response.setAllowedTransitions(WorkflowTransition.allowedFrom(record.getRecordType(), record.getStatus()));
        response.setResendCount(record.getResendCount());
        response.setStatusHistory(deserializeHistory(record.getStatusHistory()));
        response.setCreatedAt(record.getCreatedAt());
        response.setUpdatedAt(record.getUpdatedAt());
        response.setCreatedBy(record.getCreatedBy());
        response.setUpdatedBy(record.getUpdatedBy());
        response.setDisabled(Boolean.TRUE.equals(record.getIsDeleted()));
    }

    private List<StatusHistoryEntry> deserializeHistory(String json) {
        if (json == null || json.isBlank()) return List.of();
        try { return MAPPER.readValue(json, HISTORY_TYPE); }
        catch (Exception e) { return List.of(); }
    }
}
