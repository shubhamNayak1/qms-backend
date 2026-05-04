package com.qms.module.qms.common.service;

import com.qms.common.enums.QmsRecordType;
import com.qms.common.exception.AppException;
import com.qms.module.qms.capa.repository.CapaRepository;
import com.qms.module.qms.changecontrol.repository.ChangeControlRepository;
import com.qms.module.qms.common.entity.QmsRecord;
import com.qms.module.qms.complaint.repository.MarketComplaintRepository;
import com.qms.module.qms.deviation.repository.DeviationRepository;
import com.qms.module.qms.incident.repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves a generic (recordType, recordId) tuple to the underlying QMS
 * record across all 5 sub-modules.
 *
 * Used by the common service layer (line items, department comments,
 * target-date extension) so we don't need 5 copies of the same lookup logic.
 *
 * Each sub-module owns its own physical table (TABLE_PER_CLASS), so a real
 * FK isn't possible — instead we route to the right repository based on
 * the {@code recordType} discriminator.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QmsRecordLookupService {

    private final CapaRepository            capaRepository;
    private final DeviationRepository       deviationRepository;
    private final IncidentRepository        incidentRepository;
    private final ChangeControlRepository   changeControlRepository;
    private final MarketComplaintRepository marketComplaintRepository;

    public QmsRecord findByTypeAndId(QmsRecordType type, Long id) {
        if (type == null || id == null) {
            throw AppException.badRequest("recordType and recordId are required");
        }
        return switch (type) {
            case CAPA             -> capaRepository.findById(id)
                    .orElseThrow(() -> AppException.notFound("CAPA", id));
            case DEVIATION        -> deviationRepository.findById(id)
                    .orElseThrow(() -> AppException.notFound("Deviation", id));
            case INCIDENT         -> incidentRepository.findById(id)
                    .orElseThrow(() -> AppException.notFound("Incident", id));
            case CHANGE_CONTROL   -> changeControlRepository.findById(id)
                    .orElseThrow(() -> AppException.notFound("ChangeControl", id));
            case MARKET_COMPLAINT -> marketComplaintRepository.findById(id)
                    .orElseThrow(() -> AppException.notFound("MarketComplaint", id));
            case AUDIT_SCHEDULE   -> throw AppException.badRequest(
                    "AUDIT_SCHEDULE records are not supported by the QMS common APIs.");
        };
    }

    public QmsRecord save(QmsRecord record) {
        if (record == null) throw AppException.badRequest("record is null");
        return switch (record.getRecordType()) {
            case CAPA             -> capaRepository.save(
                    (com.qms.module.qms.capa.entity.Capa) record);
            case DEVIATION        -> deviationRepository.save(
                    (com.qms.module.qms.deviation.entity.Deviation) record);
            case INCIDENT         -> incidentRepository.save(
                    (com.qms.module.qms.incident.entity.Incident) record);
            case CHANGE_CONTROL   -> changeControlRepository.save(
                    (com.qms.module.qms.changecontrol.entity.ChangeControl) record);
            case MARKET_COMPLAINT -> marketComplaintRepository.save(
                    (com.qms.module.qms.complaint.entity.MarketComplaint) record);
            case AUDIT_SCHEDULE   -> throw AppException.badRequest(
                    "AUDIT_SCHEDULE records are not supported by the QMS common APIs.");
        };
    }
}
