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
        if (req.getTitle()                != null) record.setTitle(req.getTitle());
        if (req.getDescription()          != null) record.setDescription(req.getDescription());
        if (req.getPriority()             != null) record.setPriority(req.getPriority());
        if (req.getAssignedToId()         != null) record.setAssignedToId(req.getAssignedToId());
        if (req.getDepartment()           != null) record.setDepartment(req.getDepartment());
        if (req.getDueDate()              != null) record.setDueDate(req.getDueDate());
        if (req.getTargetCompletionDate() != null) record.setTargetCompletionDate(req.getTargetCompletionDate());
        if (req.getRootCause()            != null) record.setRootCause(req.getRootCause());
        if (req.getCorrectiveAction()     != null) record.setCorrectiveAction(req.getCorrectiveAction());
        if (req.getComments()             != null) record.setComments(req.getComments());
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
        response.setOverdue(record.isOverdue());
        response.setAllowedTransitions(WorkflowTransition.allowedFrom(record.getRecordType(), record.getStatus()));
        response.setStatusHistory(deserializeHistory(record.getStatusHistory()));
        response.setCreatedAt(record.getCreatedAt());
        response.setUpdatedAt(record.getUpdatedAt());
        response.setCreatedBy(record.getCreatedBy());
        response.setUpdatedBy(record.getUpdatedBy());
    }

    private List<StatusHistoryEntry> deserializeHistory(String json) {
        if (json == null || json.isBlank()) return List.of();
        try { return MAPPER.readValue(json, HISTORY_TYPE); }
        catch (Exception e) { return List.of(); }
    }
}
