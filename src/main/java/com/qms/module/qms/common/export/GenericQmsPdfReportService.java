package com.qms.module.qms.common.export;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import com.qms.common.enums.QmsRecordType;
import com.qms.common.exception.AppException;
import com.qms.module.qms.common.dto.response.QmsBaseResponse;
import com.qms.module.qms.common.dto.response.QmsLineItemResponse;
import com.qms.module.qms.common.service.QmsLineItemService;
import com.qms.module.qms.common.workflow.StatusHistoryEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

import static com.qms.module.qms.common.export.QmsPdfReportSupport.*;

/**
 * GenericQmsPdfReportService — one exporter that produces a serviceable
 * PDF for any QMS module (CAPA, Deviation, Incident, Market Complaint).
 *
 * The Change Control exporter remains dedicated because its per-stage
 * layout is finely tuned to the CC workflow; the four modules below
 * share enough shape that a single generic renderer covers them.
 *
 * Report structure:
 *   1. Cover header       — record number, module, raised-by/on, status
 *   2. Core fields        — title, department, priority, dates
 *   3. Narrative sections — description, root cause / investigation /
 *                            resolution per module (passed by caller)
 *   4. Line items         — reused across every module via QmsLineItemService
 *   5. Approval + closure — approved by, when, comments
 *   6. Status history     — chronological transitions with remark
 *
 * All text fields pass through QmsPdfReportSupport.safe() which strips
 * stored HTML (R.3) so Quill output renders as prose.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GenericQmsPdfReportService {

    private final QmsLineItemService lineItemService;

    @Value("${reports.export.company-name:QMS Organisation}")
    private String companyName;

    /**
     * Render a QMS record to PDF bytes.
     *
     * @param rec    the record response (any subclass of QmsBaseResponse)
     * @param module human-friendly module name shown in the header ("CAPA",
     *               "Deviation", "Incident", "Market Complaint")
     * @param recordType enum used to fetch line items for that module
     * @param extras ordered map of module-specific label→value rows the
     *               generic renderer wouldn't know about (e.g. CAPA's
     *               effectivenessCheckDate, Deviation's deviationType).
     */
    public byte[] render(QmsBaseResponse rec, String module,
                         QmsRecordType recordType, Map<String, String> extras) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 30, 30, 40, 40);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new PageFooter(
                    companyName, rec.getRecordNumber(), rec.getRaisedByName()));
            doc.open();

            renderCoverHeader(doc, rec, module);
            renderCoreFields(doc, rec, extras);
            renderNarratives(doc, rec);
            renderLineItems(doc, rec, recordType);
            renderApproval(doc, rec);
            renderStatusHistory(doc, rec);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to render QMS PDF report", e);
            throw AppException.internalError("Failed to render PDF: " + e.getMessage());
        }
    }

    // ─── Sections ────────────────────────────────────────────

    private void renderCoverHeader(Document doc, QmsBaseResponse rec, String module)
            throws DocumentException {
        Paragraph title = new Paragraph(module.toUpperCase() + " REPORT", FONT_TITLE);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(6);
        doc.add(title);

        Paragraph sub = new Paragraph(
                "Generated " + DT_FMT.format(LocalDateTime.now()) + " · " + companyName,
                FONT_BODY);
        sub.setAlignment(Element.ALIGN_CENTER);
        sub.setSpacingAfter(14);
        doc.add(sub);

        PdfPTable t = twoColTable();
        addKv(t, "Record No.",     safe(rec.getRecordNumber()));
        addKv(t, "Title",          safe(rec.getTitle()));
        addKv(t, "Module",         module);
        addKv(t, "Status",         rec.getStatus() == null ? "—" : rec.getStatus().toString());
        addKv(t, "Priority",       rec.getPriority() == null ? "—" : rec.getPriority().toString());
        addKv(t, "Department",     safe(rec.getDepartment()));
        addKv(t, "Raised By",      safe(rec.getRaisedByName()));
        addKv(t, "Raised On",      rec.getCreatedAt() == null ? "—" : DT_FMT.format(rec.getCreatedAt()));
        addKv(t, "Due Date",       rec.getDueDate() == null ? "—" : DATE_FMT.format(rec.getDueDate()));
        addKv(t, "Closed Date",    rec.getClosedDate() == null ? "—" : DATE_FMT.format(rec.getClosedDate()));
        doc.add(t);
        doc.add(new Paragraph(" "));
    }

    private void renderCoreFields(Document doc, QmsBaseResponse rec, Map<String, String> extras)
            throws DocumentException {
        if (extras == null || extras.isEmpty()) return;
        sectionBanner(doc, "Module Fields");
        PdfPTable t = twoColTable();
        for (Map.Entry<String, String> e : extras.entrySet()) {
            addKv(t, e.getKey(), safe(e.getValue()));
        }
        doc.add(t);
        doc.add(new Paragraph(" "));
    }

    private void renderNarratives(Document doc, QmsBaseResponse rec)
            throws DocumentException {
        sectionBanner(doc, "Narrative");
        PdfPTable t = singleColTable();
        addLabeled(t, "Description",         safe(rec.getDescription()));
        if (rec.getInitialAssessment() != null && !rec.getInitialAssessment().isBlank())
            addLabeled(t, "Initial Assessment", safe(rec.getInitialAssessment()));
        if (rec.getRiskAssessment() != null && !rec.getRiskAssessment().isBlank())
            addLabeled(t, "Risk Assessment",    safe(rec.getRiskAssessment()));
        if (rec.getRootCause() != null && !rec.getRootCause().isBlank())
            addLabeled(t, "Root Cause",         safe(rec.getRootCause()));
        if (rec.getCorrectiveAction() != null && !rec.getCorrectiveAction().isBlank())
            addLabeled(t, "Corrective Action",  safe(rec.getCorrectiveAction()));
        if (rec.getCustomerComment() != null && !rec.getCustomerComment().isBlank())
            addLabeled(t, "Customer Comment",   safe(rec.getCustomerComment()));
        doc.add(t);
        doc.add(new Paragraph(" "));
    }

    private void renderLineItems(Document doc, QmsBaseResponse rec, QmsRecordType recordType)
            throws DocumentException {
        if (rec.getId() == null || recordType == null) return;
        List<QmsLineItemResponse> items;
        try {
            items = lineItemService.list(recordType, rec.getId());
        } catch (Exception e) {
            // Not every module always has line items; swallow.
            return;
        }
        if (items == null || items.isEmpty()) return;

        sectionBanner(doc, "Line Items (" + items.size() + ")");
        PdfPTable t = new PdfPTable(new float[]{0.4f, 2f, 2f, 2.5f});
        t.setWidthPercentage(100);
        addHeader(t, "#");
        addHeader(t, "Existing System");
        addHeader(t, "Proposed System");
        addHeader(t, "Remark / Justification");
        for (QmsLineItemResponse li : items) {
            t.addCell(cell(String.valueOf(li.getSrNo()), null));
            t.addCell(cell(safe(li.getExistingSystem()), null));
            t.addCell(cell(safe(li.getProposedSystem()), null));
            t.addCell(cell(safe(li.getJustification()), null));
        }
        doc.add(t);
        doc.add(new Paragraph(" "));
    }

    private void renderApproval(Document doc, QmsBaseResponse rec) throws DocumentException {
        if (rec.getApprovedByName() == null && rec.getApprovalComments() == null) return;
        sectionBanner(doc, "Approval & Closure");
        PdfPTable t = twoColTable();
        if (rec.getApprovedByName() != null) addKv(t, "Approved By", safe(rec.getApprovedByName()));
        if (rec.getApprovedAt() != null)     addKv(t, "Approved At", DT_FMT.format(rec.getApprovedAt()));
        if (rec.getClosedDate() != null)     addKv(t, "Closed Date", DATE_FMT.format(rec.getClosedDate()));
        doc.add(t);
        if (rec.getApprovalComments() != null && !rec.getApprovalComments().isBlank()) {
            PdfPTable c = singleColTable();
            addLabeled(c, "Approval Comments", safe(rec.getApprovalComments()));
            doc.add(c);
        }
        doc.add(new Paragraph(" "));
    }

    private void renderStatusHistory(Document doc, QmsBaseResponse rec) throws DocumentException {
        List<StatusHistoryEntry> history = rec.getStatusHistory();
        if (history == null || history.isEmpty()) return;
        sectionBanner(doc, "Status History (" + history.size() + ")");
        PdfPTable t = new PdfPTable(new float[]{1.3f, 1.5f, 1.5f, 1.2f, 2.5f});
        t.setWidthPercentage(100);
        addHeader(t, "At");
        addHeader(t, "From");
        addHeader(t, "To");
        addHeader(t, "By");
        addHeader(t, "Remark");
        for (StatusHistoryEntry h : history) {
            t.addCell(cell(h.getChangedAt() == null ? "—" : DT_FMT.format(h.getChangedAt()), null));
            t.addCell(cell(h.getFromStatus() == null ? "—" : h.getFromStatus().toString(), null));
            t.addCell(cell(h.getToStatus()   == null ? "—" : h.getToStatus().toString(),   null));
            t.addCell(cell(safe(h.getChangedByUsername()), null));
            t.addCell(cell(safe(h.getComment()), null));
        }
        doc.add(t);
    }

    // ─── Public helper: pre-filled extras map (preserves order) ──
    public static Map<String, String> extras() {
        return new LinkedHashMap<>();
    }
}
