package com.qms.module.reports.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qms.common.enums.AuditAction;
import com.qms.common.enums.AuditModule;
import com.qms.common.exception.AppException;
import com.qms.module.audit.annotation.Audited;
import com.qms.common.response.PageResponse;
import com.qms.module.reports.aggregation.DynamicReportBuilder;
import com.qms.module.reports.aggregation.ModuleFieldRegistry;
import com.qms.module.reports.dto.request.CreateReportRequest;
import com.qms.module.reports.dto.request.UpdateReportRequest;
import com.qms.module.reports.dto.response.*;
import com.qms.module.reports.entity.ReportRunHistory;
import com.qms.module.reports.entity.SavedReport;
import com.qms.module.reports.enums.*;
import com.qms.module.reports.export.CsvExporter;
import com.qms.module.reports.export.DynamicExcelExporter;
import com.qms.module.reports.repository.ReportRunHistoryRepository;
import com.qms.module.reports.repository.SavedReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportManagerService {

    private final SavedReportRepository      reportRepository;
    private final ReportRunHistoryRepository historyRepository;
    private final DynamicReportBuilder       reportBuilder;
    private final DynamicExcelExporter       excelExporter;
    private final CsvExporter               csvExporter;
    private final ObjectMapper               objectMapper;

    @Value("${reports.storage.path:./report-files}")
    private String storagePath;

    // ── Create ────────────────────────────────────────────────

    @Audited(action = AuditAction.CREATE, module = AuditModule.REPORT,
             entityType = "SavedReport", description = "Dynamic report created and executed")
    @Transactional
    public ReportResponse create(CreateReportRequest req) {
        ModuleFieldRegistry.validateFields(req.getModule(), req.getDimensions(), req.getMetrics());

        SavedReport report = SavedReport.builder()
                .name(req.getName())
                .description(req.getDescription())
                .module(req.getModule())
                .format(req.getFormat() != null ? req.getFormat() : ExportFormat.EXCEL)
                .dateFrom(req.getDateFrom())
                .dateTo(req.getDateTo())
                .dimensions(toJson(req.getDimensions()))
                .metrics(toJson(req.getMetrics()))
                .extraFilters(toJson(req.getExtraFilters()))
                .status(ReportStatus.PENDING)
                .createdByUserId(currentUserId())
                .createdByUsername(currentUsername())
                .build();

        report = reportRepository.save(report);
        log.info("Report '{}' created by {} for module {}", report.getName(), currentUsername(), report.getModule());

        // Auto-run on creation
        return toResponse(executeReport(report));
    }

    // ── Update ────────────────────────────────────────────────

    @Audited(action = AuditAction.UPDATE, module = AuditModule.REPORT,
             entityType = "SavedReport", entityIdArgIndex = 0,
             captureOldValue = true, description = "Report configuration updated and re-executed")
    @Transactional
    public ReportResponse update(Long id, UpdateReportRequest req) {
        SavedReport report = findById(id);
        if (req.getName()        != null) report.setName(req.getName());
        if (req.getDescription() != null) report.setDescription(req.getDescription());
        if (req.getFormat()      != null) report.setFormat(req.getFormat());
        if (req.getDateFrom()    != null) report.setDateFrom(req.getDateFrom());
        if (req.getDateTo()      != null) report.setDateTo(req.getDateTo());
        if (req.getDimensions()  != null) {
            ModuleFieldRegistry.validateFields(report.getModule(), req.getDimensions(), null);
            report.setDimensions(toJson(req.getDimensions()));
        }
        if (req.getMetrics()     != null) {
            ModuleFieldRegistry.validateFields(report.getModule(), null, req.getMetrics());
            report.setMetrics(toJson(req.getMetrics()));
        }
        if (req.getExtraFilters() != null) report.setExtraFilters(toJson(req.getExtraFilters()));

        // Reset status so it re-runs with new config
        report.setStatus(ReportStatus.PENDING);
        report = reportRepository.save(report);
        return toResponse(executeReport(report));
    }

    // ── Disable / Enable ──────────────────────────────────────

    @Audited(action = AuditAction.UPDATE, module = AuditModule.REPORT,
             entityType = "SavedReport", entityIdArgIndex = 0,
             description = "Report disabled")
    @Transactional
    public ReportResponse disable(Long id) {
        SavedReport report = findById(id);
        report.setIsDisabled(true);
        report.setStatus(ReportStatus.DISABLED);
        return toResponse(reportRepository.save(report));
    }

    @Audited(action = AuditAction.UPDATE, module = AuditModule.REPORT,
             entityType = "SavedReport", entityIdArgIndex = 0,
             description = "Report enabled")
    @Transactional
    public ReportResponse enable(Long id) {
        SavedReport report = findById(id);
        report.setIsDisabled(false);
        report.setStatus(report.getFilePath() != null ? ReportStatus.COMPLETED : ReportStatus.PENDING);
        return toResponse(reportRepository.save(report));
    }

    // ── Re-run ────────────────────────────────────────────────

    @Audited(action = AuditAction.EXPORT, module = AuditModule.REPORT,
             entityType = "SavedReport", entityIdArgIndex = 0,
             description = "Report manually re-executed")
    @Transactional
    public ReportResponse reRun(Long id) {
        SavedReport report = findById(id);
        if (Boolean.TRUE.equals(report.getIsDisabled())) {
            throw AppException.badRequest("Report is disabled. Enable it before re-running.");
        }
        return toResponse(executeReport(report));
    }

    // ── List ──────────────────────────────────────────────────

    public PageResponse<ReportResponse> listAll(int page, int size) {
        return PageResponse.of(
                reportRepository.findByIsDeletedFalseOrderByCreatedAtDesc(
                        PageRequest.of(page, size)).map(this::toResponse));
    }

    public PageResponse<ReportResponse> listByCurrentUser(int page, int size) {
        Long userId = currentUserId();
        if (userId == null) return listAll(page, size);
        return PageResponse.of(
                reportRepository.findByCreatedByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(
                        userId, PageRequest.of(page, size)).map(this::toResponse));
    }

    public PageResponse<ReportResponse> listByModule(ReportModule module, int page, int size) {
        return PageResponse.of(
                reportRepository.findByModuleAndIsDeletedFalseOrderByCreatedAtDesc(
                        module, PageRequest.of(page, size)).map(this::toResponse));
    }

    // ── History ───────────────────────────────────────────────

    public PageResponse<ReportHistoryResponse> history(Long id, int page, int size) {
        findById(id); // validate exists
        return PageResponse.of(
                historyRepository.findByReportIdOrderByStartedAtDesc(
                        id, PageRequest.of(page, size)).map(this::toHistoryResponse));
    }

    // ── Download ──────────────────────────────────────────────

    @Audited(action = AuditAction.DOWNLOAD, module = AuditModule.REPORT,
             entityType = "SavedReport", entityIdArgIndex = 0,
             captureNewValue = false, description = "Report file downloaded")
    public byte[] download(Long id) {
        SavedReport report = findById(id);
        if (report.getFilePath() == null || report.getStatus() != ReportStatus.COMPLETED) {
            throw AppException.badRequest("Report file is not ready. Run the report first.");
        }
        Path path = Paths.get(report.getFilePath());
        if (!Files.exists(path)) {
            throw AppException.badRequest("Report file not found on disk. Please re-run the report.");
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw AppException.internalError("Failed to read report file: " + e.getMessage());
        }
    }

    public SavedReport getById(Long id) {
        return findById(id);
    }

    // ── Fields Metadata ───────────────────────────────────────

    public ModuleFieldsResponse getModuleFields(ReportModule module) {
        return ModuleFieldsResponse.builder()
                .module(module)
                .dimensions(ModuleFieldRegistry.dimensionsFor(module).stream()
                        .map(f -> ModuleFieldsResponse.FieldInfo.builder()
                                .key(f.getKey()).label(f.getLabel()).build())
                        .toList())
                .metrics(ModuleFieldRegistry.metricsFor(module).stream()
                        .map(f -> ModuleFieldsResponse.FieldInfo.builder()
                                .key(f.getKey()).label(f.getLabel()).build())
                        .toList())
                .build();
    }

    // ── Execution ─────────────────────────────────────────────

    @Transactional
    public SavedReport executeReport(SavedReport report) {
        LocalDateTime started = LocalDateTime.now();
        report.setStatus(ReportStatus.RUNNING);
        report = reportRepository.save(report);

        ReportRunHistory history = ReportRunHistory.builder()
                .reportId(report.getId())
                .triggeredByUserId(currentUserId())
                .triggeredByUsername(currentUsername())
                .status(RunStatus.RUNNING)
                .startedAt(started)
                .build();
        history = historyRepository.save(history);

        try {
            List<Map<String, Object>> rows = reportBuilder.execute(report);

            // Generate file
            byte[] fileBytes = report.getFormat() == ExportFormat.CSV
                    ? csvExporter.export(rows)
                    : excelExporter.export(rows, report.getName(), report.getDateFrom(), report.getDateTo());

            String ext = report.getFormat() == ExportFormat.CSV ? "csv" : "xlsx";
            String fileName = sanitize(report.getName()) + "_"
                    + DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(started) + "." + ext;

            Path dir = Paths.get(storagePath);
            Files.createDirectories(dir);
            Path filePath = dir.resolve(fileName);
            Files.write(filePath, fileBytes);

            long durationMs = java.time.Duration.between(started, LocalDateTime.now()).toMillis();

            // Update report
            report.setFilePath(filePath.toAbsolutePath().toString());
            report.setFileName(fileName);
            report.setFileSizeBytes((long) fileBytes.length);
            report.setStatus(ReportStatus.COMPLETED);
            report.setLastRunAt(started);
            report.setRunCount(report.getRunCount() + 1);
            report.setLastRunError(null);
            report = reportRepository.save(report);

            // Update history
            history.setStatus(RunStatus.COMPLETED);
            history.setCompletedAt(LocalDateTime.now());
            history.setDurationMs(durationMs);
            history.setRowCount((long) rows.size());
            history.setFilePath(filePath.toAbsolutePath().toString());
            history.setFileSizeBytes((long) fileBytes.length);
            historyRepository.save(history);

            log.info("Report '{}' completed: {} rows, {}ms", report.getName(), rows.size(), durationMs);
            return report;

        } catch (Exception e) {
            log.error("Report '{}' failed: {}", report.getName(), e.getMessage(), e);

            report.setStatus(ReportStatus.FAILED);
            report.setLastRunError(e.getMessage());
            report.setRunCount(report.getRunCount() + 1);
            report = reportRepository.save(report);

            history.setStatus(RunStatus.FAILED);
            history.setCompletedAt(LocalDateTime.now());
            history.setDurationMs(java.time.Duration.between(started, LocalDateTime.now()).toMillis());
            history.setErrorMessage(e.getMessage());
            historyRepository.save(history);

            return report;
        }
    }

    // ── Helpers ───────────────────────────────────────────────

    private SavedReport findById(Long id) {
        return reportRepository.findById(id)
                .filter(r -> !Boolean.TRUE.equals(r.getIsDeleted()))
                .orElseThrow(() -> AppException.notFound("Report", id));
    }

    private ReportResponse toResponse(SavedReport r) {
        return ReportResponse.builder()
                .id(r.getId())
                .name(r.getName())
                .description(r.getDescription())
                .module(r.getModule())
                .format(r.getFormat())
                .dateFrom(r.getDateFrom())
                .dateTo(r.getDateTo())
                .dimensions(fromJson(r.getDimensions()))
                .metrics(fromJson(r.getMetrics()))
                .status(r.getStatus())
                .fileName(r.getFileName())
                .fileSizeBytes(r.getFileSizeBytes())
                .lastRunAt(r.getLastRunAt())
                .runCount(r.getRunCount())
                .lastRunError(r.getLastRunError())
                .isDisabled(r.getIsDisabled())
                .createdByUsername(r.getCreatedByUsername())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .downloadUrl(r.getStatus() == ReportStatus.COMPLETED
                        ? "/api/v1/reports/" + r.getId() + "/download" : null)
                .build();
    }

    private ReportHistoryResponse toHistoryResponse(ReportRunHistory h) {
        return ReportHistoryResponse.builder()
                .id(h.getId())
                .reportId(h.getReportId())
                .triggeredByUsername(h.getTriggeredByUsername())
                .status(h.getStatus())
                .startedAt(h.getStartedAt())
                .completedAt(h.getCompletedAt())
                .durationMs(h.getDurationMs())
                .rowCount(h.getRowCount())
                .fileSizeBytes(h.getFileSizeBytes())
                .errorMessage(h.getErrorMessage())
                .createdAt(h.getCreatedAt())
                .build();
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try { return objectMapper.writeValueAsString(obj); }
        catch (Exception e) { return null; }
    }

    @SuppressWarnings("unchecked")
    private List<String> fromJson(String json) {
        if (json == null || json.isBlank()) return null;
        try { return objectMapper.readValue(json, List.class); }
        catch (Exception e) { return null; }
    }

    private String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_").substring(0, Math.min(name.length(), 50));
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "SYSTEM";
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.qms.security.UserPrincipal up) {
            return up.getId();
        }
        return null;
    }
}
