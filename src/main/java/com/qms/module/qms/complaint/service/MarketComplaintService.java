package com.qms.module.qms.complaint.service;

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
import com.qms.module.qms.complaint.dto.request.MarketComplaintRequest;
import com.qms.module.qms.complaint.dto.response.MarketComplaintResponse;
import com.qms.module.qms.complaint.entity.MarketComplaint;
import com.qms.module.qms.complaint.repository.MarketComplaintRepository;
import com.qms.module.qms.complaint.repository.MarketComplaintSpecification;
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

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MarketComplaintService {

    private static final String TABLE = "qms_market_complaint";

    private final MarketComplaintRepository complaintRepository;
    private final QmsWorkflowEngine         workflowEngine;
    private final RecordNumberGenerator     recordNumberGenerator;
    private final QmsRecordMapper           recordMapper;
    private final AuditValueSerializer      auditSerializer;

    public PageResponse<MarketComplaintResponse> search(QmsStatus status, Priority priority,
                                                         String category, Long assignedTo,
                                                         Boolean reportableOnly, String search,
                                                         int page, int size) {

        Specification<MarketComplaint> spec = MarketComplaintSpecification.filter(status,priority,category,assignedTo,reportableOnly,search);
        var pageResult = complaintRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));

        return PageResponse.of(pageResult.map(this::toResponse));
//        return PageResponse.of(
//                complaintRepository.search(status, priority, category, assignedTo,
//                        reportableOnly, search,
//                        PageRequest.of(page, size, Sort.by("createdAt").descending()))
//                        .map(this::toResponse));
    }

    public MarketComplaintResponse getById(Long id) {
        return toResponse(findById(id));
    }

    @Audited(action = AuditAction.CREATE, module = AuditModule.MARKET_COMPLAINT, entityType = "MarketComplaint", description = "MarketComplaint record created")
    @Transactional
    public MarketComplaintResponse create(MarketComplaintRequest req) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "SYSTEM";

        MarketComplaint mc = MarketComplaint.builder().build();
        mc.setRecordNumber(recordNumberGenerator.generate(QmsRecordType.MARKET_COMPLAINT, TABLE));
        mc.setStatus(QmsStatus.OPEN);
        mc.setRaisedByName(username);
        if (req.getReceivedDate() == null) mc.setReceivedDate(LocalDate.now());
        recordMapper.applyRequest(req, mc);
        applyFields(req, mc);

        MarketComplaint saved = complaintRepository.save(mc);
        log.info("Market Complaint created: {} by {}", saved.getRecordNumber(), username);
        return toResponse(saved);
    }

    @Audited(action = AuditAction.UPDATE, module = AuditModule.MARKET_COMPLAINT, entityType = "MarketComplaint", entityIdArgIndex = 0)
    @Transactional
    public MarketComplaintResponse update(Long id, MarketComplaintRequest req) {
        MarketComplaint mc = findById(id);
        AuditContextHolder.set(AuditContext.builder()
                .oldValue(auditSerializer.serialize(toResponse(mc)))
                .build());
        if (mc.isTerminal()) throw AppException.badRequest("Cannot update a " + mc.getStatus() + " Market Complaint");
        recordMapper.applyRequest(req, mc);
        applyFields(req, mc);
        return toResponse(complaintRepository.save(mc));
    }

    @Audited(action = AuditAction.UPDATE, module = AuditModule.MARKET_COMPLAINT, entityType = "MarketComplaint", entityIdArgIndex = 0)
    @Transactional
    public MarketComplaintResponse transition(Long id, WorkflowRequest req) {
        MarketComplaint mc = findById(id);
        workflowEngine.transition(mc, req.getTargetStatus(), req.getComment());
        return toResponse(complaintRepository.save(mc));
    }

    @Audited(action = AuditAction.SUBMIT, module = AuditModule.MARKET_COMPLAINT, entityType = "MarketComplaint", entityIdArgIndex = 0)
    @Transactional
    public MarketComplaintResponse submit(Long id, String comment) {
        MarketComplaint mc = findById(id);
        workflowEngine.submit(mc, comment);
        return toResponse(complaintRepository.save(mc));
    }

    @Audited(action = AuditAction.APPROVE, module = AuditModule.MARKET_COMPLAINT, entityType = "MarketComplaint", entityIdArgIndex = 0)
    @Transactional
    public MarketComplaintResponse approve(Long id, String comment) {
        MarketComplaint mc = findById(id);
        workflowEngine.approve(mc, comment);
        return toResponse(complaintRepository.save(mc));
    }

    @Audited(action = AuditAction.REJECT, module = AuditModule.MARKET_COMPLAINT, entityType = "MarketComplaint", entityIdArgIndex = 0)
    @Transactional
    public MarketComplaintResponse reject(Long id, String comment) {
        MarketComplaint mc = findById(id);
        workflowEngine.reject(mc, comment);
        return toResponse(complaintRepository.save(mc));
    }

    @Audited(action = AuditAction.CLOSE, module = AuditModule.MARKET_COMPLAINT, entityType = "MarketComplaint", entityIdArgIndex = 0)
    @Transactional
    public MarketComplaintResponse close(Long id, String comment) {
        MarketComplaint mc = findById(id);
        workflowEngine.close(mc, comment);
        return toResponse(complaintRepository.save(mc));
    }

    @Audited(action = AuditAction.CANCEL, module = AuditModule.MARKET_COMPLAINT, entityType = "MarketComplaint", entityIdArgIndex = 0)
    @Transactional
    public MarketComplaintResponse cancel(Long id, String comment) {
        MarketComplaint mc = findById(id);
        workflowEngine.cancel(mc, comment);
        return toResponse(complaintRepository.save(mc));
    }

    @Audited(action = AuditAction.REOPEN, module = AuditModule.MARKET_COMPLAINT, entityType = "MarketComplaint", entityIdArgIndex = 0)
    @Transactional
    public MarketComplaintResponse reopen(Long id, String comment) {
        MarketComplaint mc = findById(id);
        workflowEngine.reopen(mc, comment);
        return toResponse(complaintRepository.save(mc));
    }

    @Audited(action = AuditAction.DELETE, module = AuditModule.MARKET_COMPLAINT, entityType = "MarketComplaint", entityIdArgIndex = 0, captureNewValue = false, description = "MarketComplaint record deleted")
    @Transactional
    public void delete(Long id) {
        MarketComplaint mc = findById(id);
        AuditContextHolder.set(AuditContext.builder()
                .oldValue(auditSerializer.serialize(toResponse(mc)))
                .build());
        mc.setIsDeleted(true);
        complaintRepository.save(mc);
    }

    private MarketComplaint findById(Long id) {
        return complaintRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> AppException.notFound("Market Complaint", id));
    }

    private void applyFields(MarketComplaintRequest req, MarketComplaint mc) {
        if (req.getCustomerName()              != null) mc.setCustomerName(req.getCustomerName());
        if (req.getCustomerContact()           != null) mc.setCustomerContact(req.getCustomerContact());
        if (req.getCustomerCountry()           != null) mc.setCustomerCountry(req.getCustomerCountry());
        if (req.getProductName()               != null) mc.setProductName(req.getProductName());
        if (req.getBatchNumber()               != null) mc.setBatchNumber(req.getBatchNumber());
        if (req.getExpiryDate()                != null) mc.setExpiryDate(req.getExpiryDate());
        if (req.getComplaintCategory()         != null) mc.setComplaintCategory(req.getComplaintCategory());
        if (req.getComplaintSource()           != null) mc.setComplaintSource(req.getComplaintSource());
        if (req.getReceivedDate()              != null) mc.setReceivedDate(req.getReceivedDate());
        if (req.getReportableToAuthority()     != null) mc.setReportableToAuthority(req.getReportableToAuthority());
        if (req.getAuthorityReportReference()  != null) mc.setAuthorityReportReference(req.getAuthorityReportReference());
        if (req.getAuthorityReportDate()       != null) mc.setAuthorityReportDate(req.getAuthorityReportDate());
        if (req.getResolutionDetails()         != null) mc.setResolutionDetails(req.getResolutionDetails());
        if (req.getCustomerResponse()          != null) mc.setCustomerResponse(req.getCustomerResponse());
        if (req.getCustomerNotifiedDate()      != null) mc.setCustomerNotifiedDate(req.getCustomerNotifiedDate());
        if (req.getCustomerSatisfied()         != null) mc.setCustomerSatisfied(req.getCustomerSatisfied());
        if (req.getCapaReference()             != null) mc.setCapaReference(req.getCapaReference());
        if (req.getSampleReturned()            != null) mc.setSampleReturned(req.getSampleReturned());
    }

    private MarketComplaintResponse toResponse(MarketComplaint mc) {
        MarketComplaintResponse r = new MarketComplaintResponse();
        recordMapper.applyResponse(mc, r);
        r.setCustomerName(mc.getCustomerName());
        r.setCustomerContact(mc.getCustomerContact());
        r.setCustomerCountry(mc.getCustomerCountry());
        r.setProductName(mc.getProductName());
        r.setBatchNumber(mc.getBatchNumber());
        r.setExpiryDate(mc.getExpiryDate());
        r.setComplaintCategory(mc.getComplaintCategory());
        r.setComplaintSource(mc.getComplaintSource());
        r.setReceivedDate(mc.getReceivedDate());
        r.setReportableToAuthority(mc.getReportableToAuthority());
        r.setAuthorityReportReference(mc.getAuthorityReportReference());
        r.setAuthorityReportDate(mc.getAuthorityReportDate());
        r.setResolutionDetails(mc.getResolutionDetails());
        r.setCustomerResponse(mc.getCustomerResponse());
        r.setCustomerNotifiedDate(mc.getCustomerNotifiedDate());
        r.setCustomerSatisfied(mc.getCustomerSatisfied());
        r.setCapaReference(mc.getCapaReference());
        r.setSampleReturned(mc.getSampleReturned());
        return r;
    }
}
