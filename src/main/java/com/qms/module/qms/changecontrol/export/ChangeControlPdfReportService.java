package com.qms.module.qms.changecontrol.export;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.*;
import com.qms.common.exception.AppException;
import com.qms.module.qms.common.dto.response.QmsDepartmentActionItemResponse;
import com.qms.module.qms.common.dto.response.QmsDepartmentCommentResponse;
import com.qms.module.qms.common.dto.response.QmsLineItemResponse;
import com.qms.module.qms.changecontrol.dto.response.ChangeControlResponse;
import com.qms.module.qms.common.service.QmsDepartmentActionItemService;
import com.qms.module.qms.common.service.QmsDepartmentCommentService;
import com.qms.module.qms.common.service.QmsLineItemService;
import com.qms.common.enums.QmsRecordType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Round-N (2026-07-04) tester CC-Point-2 · new "Report" ask.
 *
 * Generates a single-page-per-section PDF for a Change Control record
 * matching the reference layout the tester supplied (Vinfro CC form).
 * Sections rendered:
 *   1. Header (company + record number)
 *   2. Initiation of Change (initiator's captured fields + line items)
 *   3. Review (HOD Assessment: impact panel + Initial Risk Assessment +
 *      Initial Assessment)
 *   4. Evaluation by QA (pre-remark, post-remark, communication flags)
 *   5. Department Comments (Department / Comment / Target Date / Actor
 *      table plus action items when present)
 *   6. Site Head Concurrence
 *   7. Customer Representative comments
 *   8. Regulatory Affairs Evaluation
 *   9. Head QA Approval
 *  10. Verification of Change Implementation
 *  11. Verification Review + Approval
 * Footer on every page: application / user / timestamp / page X of Y.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChangeControlPdfReportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DT_FMT   = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    private static final Font FONT_HEADER  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.BLACK);
    private static final Font FONT_TITLE   = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.BLACK);
    private static final Font FONT_SECTION = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.BLACK);
    private static final Font FONT_LABEL   = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.BLACK);
    private static final Font FONT_BODY    = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.BLACK);
    private static final Font FONT_FOOTER  = FontFactory.getFont(FontFactory.HELVETICA, 7, Color.DARK_GRAY);

    private static final Color SECTION_BG = new Color(230, 230, 230);
    private static final Color BORDER     = new Color(140, 140, 140);

    private final QmsLineItemService              lineItemService;
    private final QmsDepartmentCommentService     deptCommentService;
    private final QmsDepartmentActionItemService  actionItemService;

    @Value("${reports.export.company-name:QMS Organisation}")
    private String companyName;

    @Value("${reports.export.company-location:Pune}")
    private String companyLocation;

    // ── Public API ───────────────────────────────────────────

    public byte[] render(ChangeControlResponse cc) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 30, 30, 40, 40);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new PageFooter(cc.getRecordNumber(), cc.getRaisedByName()));
            doc.open();

            renderCoverHeader(doc, cc);
            renderInitiation(doc, cc);
            renderReview(doc, cc);
            renderQaEvaluation(doc, cc);
            renderDepartmentComments(doc, cc);
            renderSiteHead(doc, cc);
            renderCustomer(doc, cc);
            renderRegulatoryAffairs(doc, cc);
            renderHeadQaApproval(doc, cc);
            renderVerification(doc, cc);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("CC PDF generation failed for {}: {}",
                    cc != null ? cc.getRecordNumber() : "?", e.getMessage(), e);
            throw AppException.internalError("PDF generation failed: " + e.getMessage());
        }
    }

    // ── Section renderers ────────────────────────────────────

    private void renderCoverHeader(Document doc, ChangeControlResponse cc) throws DocumentException {
        Paragraph company = new Paragraph(companyName, FONT_HEADER);
        company.setAlignment(Element.ALIGN_CENTER);
        doc.add(company);
        Paragraph loc = new Paragraph(companyLocation, FONT_BODY);
        loc.setAlignment(Element.ALIGN_CENTER);
        doc.add(loc);
        Paragraph title = new Paragraph("CHANGE CONTROL FORM", FONT_TITLE);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(6);
        doc.add(title);

        PdfPTable no = new PdfPTable(1);
        no.setWidthPercentage(100);
        no.addCell(cell(bold("Change Control No. : ") + safe(cc.getRecordNumber()), null));
        doc.add(no);
    }

    private void renderInitiation(Document doc, ChangeControlResponse cc) throws DocumentException {
        sectionBanner(doc, "INITIATION OF CHANGE");

        PdfPTable t = twoColTable();
        addKv(t, "Change Control No.", safe(cc.getRecordNumber()));
        addKv(t, "Date Of Initiation",
                cc.getCreatedAt() != null ? DATE_FMT.format(cc.getCreatedAt()) : "—");
        addKv(t, "Department", safe(cc.getDepartment()));
        addKv(t, "Change Type", safe(cc.getChangeType()));
        addKv(t, "Product/Material Name", safe(cc.getProductMaterial()));
        addKv(t, "Product/Material Code", safe(cc.getProductMaterialCode()));
        if (cc.getMarketDetails() != null && !cc.getMarketDetails().isBlank()) {
            addKvSpan(t, "Market", safe(cc.getMarketDetails()));
        }
        doc.add(t);

        // Line items table
        List<QmsLineItemResponse> items = lineItemService.list(
                QmsRecordType.CHANGE_CONTROL, cc.getId());
        Paragraph liTitle = new Paragraph("Changes Proposed with Justification.", FONT_SECTION);
        liTitle.setSpacingBefore(4);
        liTitle.setSpacingAfter(3);
        doc.add(liTitle);

        PdfPTable li = new PdfPTable(new float[]{0.6f, 3f, 3f, 3f, 2f});
        li.setWidthPercentage(100);
        addHeader(li, "Sr. No");
        addHeader(li, "Existing System");
        addHeader(li, "Proposed System");
        addHeader(li, "Justification");
        addHeader(li, "Proposed By/Date");
        if (items.isEmpty()) {
            addSpanCell(li, "No line items captured.", 5);
        } else {
            int sr = 1;
            for (QmsLineItemResponse r : items) {
                addBody(li, String.valueOf(sr++));
                addBody(li, safe(r.getExistingSystem()));
                addBody(li, safe(r.getProposedSystem()));
                addBody(li, safe(r.getJustification()));
                addBody(li, (safe(r.getProposedByName()))
                        + (r.getProposedDate() != null
                            ? " / " + DATE_FMT.format(r.getProposedDate()) : ""));
            }
        }
        doc.add(li);

        PdfPTable footer = twoColTable();
        addKv(footer, "Initiator Name", safe(cc.getRaisedByName()));
        addKv(footer, "Date",
                cc.getCreatedAt() != null ? DT_FMT.format(cc.getCreatedAt()) : "—");
        doc.add(footer);
    }

    private void renderReview(Document doc, ChangeControlResponse cc) throws DocumentException {
        sectionBanner(doc, "Review");
        PdfPTable t = singleColTable();

        // Round-N Issue 1: 7 Impact checkboxes rendered as a bullet list of ticked
        // areas. Any-Other comment surfaces below when present.
        StringBuilder impacts = new StringBuilder();
        appendIfTrue(impacts, cc.getImpactOnQualification(),    "Impact on Qualification");
        appendIfTrue(impacts, cc.getImpactOnDocumentation(),    "Impact on Documentation");
        appendIfTrue(impacts, cc.getImpactOnValidation(),       "Impact on Validation");
        appendIfTrue(impacts, cc.getImpactOnMaterialSource(),   "Impact on Material Source");
        appendIfTrue(impacts, cc.getImpactRegulatoryAspects(),  "Regulatory Aspects");
        appendIfTrue(impacts, cc.getImpactOnArtworkPack(),      "Change in Artwork / Pack Size / Pack Specification");
        appendIfTrue(impacts, cc.getImpactOther(),              "Any Other");
        addLabeled(t, "Impact Assessment",
                impacts.length() == 0 ? "None ticked" : impacts.toString());
        if (Boolean.TRUE.equals(cc.getImpactOther())
                && cc.getImpactOtherComment() != null && !cc.getImpactOtherComment().isBlank()) {
            addLabeled(t, "Any-Other Comment", cc.getImpactOtherComment());
        }
        if (Boolean.TRUE.equals(cc.getInitialRiskAssessmentRequired())) {
            addLabeled(t, "Initial Risk Assessment",
                    safe(cc.getInitialRiskAssessment()));
        }
        addLabeled(t, "Initial Assessment", safe(cc.getInitialAssessment()));
        doc.add(t);
    }

    private void renderQaEvaluation(Document doc, ChangeControlResponse cc) throws DocumentException {
        sectionBanner(doc, "Evaluation By QA");
        PdfPTable t = singleColTable();
        addLabeled(t, "Pre Remark/Justification", safe(cc.getPreRemark()));
        addLabeled(t, "Post Remark/Justification", safe(cc.getComments()));
        if (cc.getRiskAssessment() != null && !cc.getRiskAssessment().isBlank()) {
            addLabeled(t, "Risk Assessment", safe(cc.getRiskAssessment()));
        }
        addLabeled(t, "Site Head Required",
                Boolean.TRUE.equals(cc.getSiteHeadRequired()) ? "Yes" : "No");
        addLabeled(t, "Customer Communication Required",
                Boolean.TRUE.equals(cc.getCustomerCommunicationRequired()) ? "Yes"
                    : Boolean.TRUE.equals(cc.getCustomerCommentRequired()) ? "Yes" : "No");
        doc.add(t);
    }

    private void renderDepartmentComments(Document doc, ChangeControlResponse cc) throws DocumentException {
        List<QmsDepartmentCommentResponse> rows = deptCommentService.list(
                QmsRecordType.CHANGE_CONTROL, cc.getId());
        if (rows.isEmpty()) return;

        sectionBanner(doc, "Evaluation & Concurrence Of Proposed Change By Concerned Head/Designee Of The Department");
        PdfPTable t = new PdfPTable(new float[]{2f, 4f, 1.5f, 2f});
        t.setWidthPercentage(100);
        addHeader(t, "Department");
        addHeader(t, "Comment");
        addHeader(t, "Target Date");
        addHeader(t, "Done By/Date");
        for (QmsDepartmentCommentResponse r : rows) {
            addBody(t, safe(r.getDepartmentName()));
            addBody(t, safe(r.getComment()));
            addBody(t, r.getTargetDate() != null ? DATE_FMT.format(r.getTargetDate()) : "—");
            addBody(t, safe(r.getDoneByName())
                    + (r.getDoneAt() != null ? " / " + DT_FMT.format(r.getDoneAt()) : ""));
        }
        doc.add(t);

        // Round-N Issue 6: emit any action items per dept row.
        for (QmsDepartmentCommentResponse r : rows) {
            List<QmsDepartmentActionItemResponse> items = actionItemService.list(r.getId());
            if (items.isEmpty()) continue;
            Paragraph p = new Paragraph(safe(r.getDepartmentName()) + " — Action Items", FONT_SECTION);
            p.setSpacingBefore(4);
            p.setSpacingAfter(2);
            doc.add(p);
            PdfPTable ait = new PdfPTable(new float[]{0.6f, 6f, 1.4f, 1.4f, 2f});
            ait.setWidthPercentage(100);
            addHeader(ait, "#");
            addHeader(ait, "Description");
            addHeader(ait, "Target Date");
            addHeader(ait, "Status");
            addHeader(ait, "Completed By/On");
            int i = 1;
            for (QmsDepartmentActionItemResponse a : items) {
                addBody(ait, String.valueOf(i++));
                addBody(ait, safe(a.getDescription()));
                addBody(ait, a.getTargetDate() != null ? DATE_FMT.format(a.getTargetDate()) : "—");
                addBody(ait, safe(a.getStatus()));
                addBody(ait, (a.getCompletedByName() != null ? safe(a.getCompletedByName()) : "—")
                        + (a.getCompletedAt() != null ? " / " + DT_FMT.format(a.getCompletedAt()) : ""));
            }
            doc.add(ait);
        }
    }

    private void renderSiteHead(Document doc, ChangeControlResponse cc) throws DocumentException {
        if (!Boolean.TRUE.equals(cc.getSiteHeadRequired())) return;
        sectionBanner(doc, "Site Head");
        PdfPTable t = singleColTable();
        addLabeled(t, "Concurrence By Site Head",
                safe(cc.getComments())); // reuse workflow-remark slot
        doc.add(t);
    }

    private void renderCustomer(Document doc, ChangeControlResponse cc) throws DocumentException {
        boolean any = Boolean.TRUE.equals(cc.getCustomerCommunicationRequired())
                || Boolean.TRUE.equals(cc.getCustomerCommentRequired())
                || (cc.getCustomerComment() != null && !cc.getCustomerComment().isBlank())
                || (cc.getCustomerRepresentative() != null && !cc.getCustomerRepresentative().isBlank());
        if (!any) return;
        sectionBanner(doc, "Customer Representative");
        PdfPTable t = singleColTable();
        addLabeled(t, "Customer Representative", safe(cc.getCustomerRepresentative()));
        addLabeled(t, "Comments By Customer", safe(cc.getCustomerComment()));
        doc.add(t);
    }

    private void renderRegulatoryAffairs(Document doc, ChangeControlResponse cc) throws DocumentException {
        boolean any = Boolean.TRUE.equals(cc.getRegulatorySubmissionRequired())
                || (cc.getRegulatorySubmissionReference() != null
                    && !cc.getRegulatorySubmissionReference().isBlank());
        if (!any) return;
        sectionBanner(doc, "Evaluation By Regulatory Affairs Department");
        PdfPTable t = singleColTable();
        addLabeled(t, "Regulatory Submission Required",
                Boolean.TRUE.equals(cc.getRegulatorySubmissionRequired()) ? "Yes" : "No");
        addLabeled(t, "Dossier Details With No.",
                safe(cc.getRegulatorySubmissionReference()));
        doc.add(t);
    }

    private void renderHeadQaApproval(Document doc, ChangeControlResponse cc) throws DocumentException {
        sectionBanner(doc, "Approval By Head QA");
        PdfPTable t = singleColTable();
        addLabeled(t, "Category / Risk Level", safe(cc.getCategory() != null ? cc.getCategory() : cc.getRiskLevel()));
        addLabeled(t, "Approval Comment", safe(cc.getApprovalComments()));
        addLabeled(t, "Change Proposal Is",
                "CLOSED".equalsIgnoreCase(String.valueOf(cc.getStatus())) ? "Approved" : safe(String.valueOf(cc.getStatus())));
        if (cc.getApprovedByName() != null) {
            addLabeled(t, "Head QA / Designee", safe(cc.getApprovedByName())
                    + (cc.getApprovedAt() != null ? "  ·  " + DT_FMT.format(cc.getApprovedAt()) : ""));
        }
        doc.add(t);
    }

    private void renderVerification(Document doc, ChangeControlResponse cc) throws DocumentException {
        boolean any = (cc.getVerificationActionTaken() != null && !cc.getVerificationActionTaken().isBlank())
                || cc.getVerificationEffectiveOn() != null
                || (cc.getVerificationOtherComments() != null && !cc.getVerificationOtherComments().isBlank());
        if (!any) return;
        sectionBanner(doc, "Verification Of Change Implementation");
        PdfPTable t = singleColTable();
        addLabeled(t, "Action Taken / Documents Closed", safe(cc.getVerificationActionTaken()));
        addLabeled(t, "Effective / Implemented On",
                cc.getVerificationEffectiveOn() != null
                    ? DATE_FMT.format(cc.getVerificationEffectiveOn()) : "—");
        addLabeled(t, "Documents Are", safe(cc.getVerificationRegCommunication()));
        addLabeled(t, "Other Comments", safe(cc.getVerificationOtherComments()));
        if (cc.getClosedDate() != null) {
            addLabeled(t, "Record Closed On", DATE_FMT.format(cc.getClosedDate()));
        }
        doc.add(t);
    }

    // ── Layout primitives ────────────────────────────────────

    private PdfPTable twoColTable() {
        PdfPTable t = new PdfPTable(new float[]{1.4f, 3f});
        t.setWidthPercentage(100);
        t.setSpacingBefore(2);
        return t;
    }

    private PdfPTable singleColTable() {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        t.setSpacingBefore(2);
        return t;
    }

    private void sectionBanner(Document doc, String text) throws DocumentException {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        t.setSpacingBefore(6);
        PdfPCell c = new PdfPCell(new Phrase(text, FONT_SECTION));
        c.setBackgroundColor(SECTION_BG);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setBorderColor(BORDER);
        c.setPadding(4);
        t.addCell(c);
        doc.add(t);
    }

    private void addKv(PdfPTable t, String label, String value) {
        PdfPCell l = new PdfPCell(new Phrase(label, FONT_LABEL));
        l.setPadding(3); l.setBorderColor(BORDER);
        t.addCell(l);
        PdfPCell v = new PdfPCell(new Phrase(": " + safe(value), FONT_BODY));
        v.setPadding(3); v.setBorderColor(BORDER);
        t.addCell(v);
    }

    private void addKvSpan(PdfPTable t, String label, String value) {
        PdfPCell l = new PdfPCell(new Phrase(label, FONT_LABEL));
        l.setPadding(3); l.setBorderColor(BORDER); l.setColspan(1);
        t.addCell(l);
        PdfPCell v = new PdfPCell(new Phrase(": " + safe(value), FONT_BODY));
        v.setPadding(3); v.setBorderColor(BORDER); v.setColspan(1);
        t.addCell(v);
    }

    private void addLabeled(PdfPTable t, String label, String value) {
        Phrase p = new Phrase();
        p.add(new Chunk(label + " : ", FONT_LABEL));
        p.add(new Chunk(safe(value), FONT_BODY));
        PdfPCell c = new PdfPCell(p);
        c.setPadding(4); c.setBorderColor(BORDER);
        t.addCell(c);
    }

    private void addHeader(PdfPTable t, String txt) {
        PdfPCell c = new PdfPCell(new Phrase(txt, FONT_LABEL));
        c.setBackgroundColor(SECTION_BG);
        c.setPadding(4);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setBorderColor(BORDER);
        t.addCell(c);
    }

    private void addBody(PdfPTable t, String txt) {
        PdfPCell c = new PdfPCell(new Phrase(safe(txt), FONT_BODY));
        c.setPadding(3);
        c.setBorderColor(BORDER);
        t.addCell(c);
    }

    private void addSpanCell(PdfPTable t, String txt, int span) {
        PdfPCell c = new PdfPCell(new Phrase(txt, FONT_BODY));
        c.setPadding(3);
        c.setColspan(span);
        c.setBorderColor(BORDER);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        t.addCell(c);
    }

    private PdfPCell cell(String txt, Color bg) {
        PdfPCell c = new PdfPCell(new Phrase(txt, FONT_BODY));
        if (bg != null) c.setBackgroundColor(bg);
        c.setPadding(3);
        c.setBorderColor(BORDER);
        return c;
    }

    // ── Utilities ────────────────────────────────────────────

    private static String safe(String s) {
        return (s == null || s.isBlank()) ? "—" : s;
    }
    private static String bold(String s) { return s; } // placeholder for future markup
    private static void appendIfTrue(StringBuilder sb, Boolean flag, String label) {
        if (Boolean.TRUE.equals(flag)) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(label);
        }
    }

    // ── Page footer ──────────────────────────────────────────

    private class PageFooter extends PdfPageEventHelper {
        private final String recordNumber;
        private final String user;
        private BaseFont     footerFont;

        PageFooter(String recordNumber, String user) {
            this.recordNumber = recordNumber;
            this.user = user;
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document doc) {
            // Round-N follow-up: mirrors PdfExporter — resolve BaseFont
            // directly rather than via FontFactory.getFont().getBaseFont(),
            // which can return null and NPE the footer.
            try {
                footerFont = BaseFont.createFont(
                        BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            } catch (Exception e) {
                log.warn("Footer BaseFont init failed: {}", e.getMessage());
            }
        }

        @Override
        public void onEndPage(PdfWriter writer, Document doc) {
            if (footerFont == null) return;
            String left = companyName + " · " + safe(recordNumber);
            String right = safe(user) + " · " + DT_FMT.format(LocalDateTime.now())
                    + " · Page " + writer.getPageNumber();
            PdfContentByte cb = writer.getDirectContent();
            cb.beginText();
            cb.setFontAndSize(footerFont, 7);
            cb.setColorFill(Color.DARK_GRAY);
            cb.showTextAligned(Element.ALIGN_LEFT, left, 30, 20, 0);
            cb.showTextAligned(Element.ALIGN_RIGHT, right, doc.getPageSize().getWidth() - 30, 20, 0);
            cb.endText();
        }
    }
}
