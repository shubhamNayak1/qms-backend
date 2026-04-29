package com.qms.module.reports.export;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.*;
import com.qms.common.exception.AppException;
import com.qms.module.reports.dto.response.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Exports report data to PDF using OpenPDF (LGPL fork of iText 4).
 *
 * Layout per report:
 *   Page 1:  Company header, report title, period, generated-at
 *   Block 1: Summary statistics table
 *   Block 2: Data table (columnar, paginated automatically by OpenPDF)
 *   Footer:  Page N of M on every page
 *
 * Best practices:
 *   ✓ PageSize.A4 landscape for wide data tables
 *   ✓ PdfPTable with relative column widths for each report type
 *   ✓ Header/footer via PdfPageEventHelper
 *   ✓ Alternating row shading (white / very light grey)
 *   ✓ Summary page before data table
 *   ✓ Max row guard to prevent PDF memory explosion
 */
@Slf4j
@Component
public class PdfExporter {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
    private static final DateTimeFormatter DT_FMT   = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm");

    private static final Color HEADER_BG  = new Color(31, 78, 121);   // dark blue
    private static final Color ALT_ROW_BG = new Color(242, 242, 242); // very light grey
    private static final Color ALERT_BG   = new Color(255, 199, 206); // soft red
    private static final Color WHITE      = Color.WHITE;

    private static final Font FONT_TITLE   = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.BLACK);
    private static final Font FONT_HEADING = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.WHITE);
    private static final Font FONT_SUBHEAD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.BLACK);
    private static final Font FONT_DATA    = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.BLACK);
    private static final Font FONT_META    = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
    private static final Font FONT_ALERT   = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, new Color(180, 0, 0));

    @Value("${reports.export.company-name:QMS Organisation}")
    private String companyName;

    @Value("${reports.export.max-rows-pdf:5000}")
    private int maxRowsPdf;

    // ── Aggregation/Summary PDF ───────────────────────────────

    public byte[] exportAggregation(List<AggregationResult> data, String title) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 40, 40, 60, 40);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new PageFooter(companyName, title));
            doc.open();
            doc.add(new Paragraph(companyName, FONT_SUBHEAD));
            doc.add(new Paragraph(title, FONT_TITLE));
            doc.add(new Paragraph("Generated: " + DT_FMT.format(LocalDateTime.now()), FONT_META));
            doc.add(Chunk.NEWLINE);

            PdfPTable table = createTable(new float[]{3f, 1.5f, 1.5f},
                    "Category", "Count", "Percentage (%)");
            for (int i = 0; i < data.size(); i++) {
                AggregationResult r = data.get(i);
                Color bg = i % 2 == 0 ? WHITE : ALT_ROW_BG;
                addCell(table, r.getLabel(), bg);
                addCell(table, String.valueOf(r.getCount() != null ? r.getCount() : 0), bg);
                addCell(table, r.getPercentage() != null
                        ? String.format("%.1f%%", r.getPercentage()) : "—", bg);
            }
            doc.add(table);
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw AppException.internalError("PDF generation failed: " + e.getMessage());
        }
    }

    // ── Layout helpers ────────────────────────────────────────

    private void addReportHeader(Document doc, String title, ReportSummary summary)
            throws DocumentException {
        doc.add(new Paragraph(companyName, FONT_META));
        Paragraph t = new Paragraph(title != null ? title : "QMS Report", FONT_TITLE);
        t.setSpacingAfter(4);
        doc.add(t);
        if (summary != null && summary.getPeriodFrom() != null) {
            doc.add(new Paragraph(
                    "Period: " + summary.getPeriodFrom() + " to " + summary.getPeriodTo(), FONT_META));
        }
        doc.add(new Paragraph("Generated: " + DT_FMT.format(LocalDateTime.now()), FONT_META));
        doc.add(Chunk.NEWLINE);
    }

    private void addSummaryTable(Document doc, ReportSummary s) throws DocumentException {
        Paragraph heading = new Paragraph("Summary", FONT_SUBHEAD);
        heading.setSpacingBefore(6); heading.setSpacingAfter(4);
        doc.add(heading);

        PdfPTable t = new PdfPTable(6);
        t.setWidthPercentage(100);
        t.setSpacingAfter(10);
        addSummaryCell(t, "Total Records", String.valueOf(s.getTotalRecords()));
        addSummaryCell(t, "Open",           String.valueOf(s.getOpenCount()));
        addSummaryCell(t, "In Progress",    String.valueOf(s.getInProgressCount()));
        addSummaryCell(t, "Closed",         String.valueOf(s.getClosedCount()));
        addSummaryCell(t, "Overdue",        String.valueOf(s.getOverdueCount()));
        addSummaryCell(t, "Avg Resolution", s.getAvgResolutionDays() != null
                ? s.getAvgResolutionDays() + " days" : "N/A");
        doc.add(t);
    }

    private void addSummaryCell(PdfPTable t, String label, String value) {
        PdfPCell lc = new PdfPCell(new Phrase(label, FONT_META));
        lc.setBorder(Rectangle.BOTTOM); lc.setPadding(3);
        t.addCell(lc);
        PdfPCell vc = new PdfPCell(new Phrase(value, FONT_SUBHEAD));
        vc.setBorder(Rectangle.BOTTOM); vc.setPadding(3);
        t.addCell(vc);
    }

    private PdfPTable createTable(float[] widths, String... headers) throws DocumentException {
        PdfPTable table = new PdfPTable(headers.length);
        table.setWidthPercentage(100);
        table.setWidths(widths);
        table.setHeaderRows(1);
        table.setSpacingBefore(4);
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, FONT_HEADING));
            cell.setBackgroundColor(HEADER_BG);
            cell.setPadding(5); cell.setBorder(Rectangle.NO_BORDER);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }
        return table;
    }

    private void addCell(PdfPTable table, String value, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(value != null ? value : "", FONT_DATA));
        cell.setBackgroundColor(bg);
        cell.setPadding(3); cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(new Color(220, 220, 220));
        table.addCell(cell);
    }

    private void guardRowCount(int count) {
        if (count > maxRowsPdf) {
            throw AppException.badRequest(
                    "PDF export is limited to " + maxRowsPdf + " rows. Apply filters or use Excel format.");
        }
    }

    private String fmt(LocalDate d)     { return d  != null ? DATE_FMT.format(d) : ""; }
    private String bool(Boolean b)      { return b  != null ? (b ? "Yes" : "No") : ""; }
    private String truncate(String s, int max) {
        return s == null ? "" : s.length() <= max ? s : s.substring(0, max) + "…";
    }

    // ── Page footer event ─────────────────────────────────────

    private static class PageFooter extends PdfPageEventHelper {
        private final String company;
        private final String title;
        private PdfTemplate totalTemplate;
        private BaseFont    baseFont;

        PageFooter(String company, String title) {
            this.company = company;
            this.title   = title;
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            totalTemplate = writer.getDirectContent().createTemplate(30, 16);
            try { baseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED); }
            catch (Exception ignored) {}
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            cb.beginText();
            cb.setFontAndSize(baseFont, 8);
            float y = document.bottom() - 10;
            cb.setTextMatrix(document.left(), y);
            cb.showText(company + " | " + title + " | CONFIDENTIAL");
            cb.setTextMatrix(document.right() - 60, y);
            cb.showText("Page " + writer.getPageNumber() + " of ");
            cb.endText();
            cb.addTemplate(totalTemplate, document.right() - 30, y);
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            totalTemplate.beginText();
            totalTemplate.setFontAndSize(baseFont, 8);
            totalTemplate.setTextMatrix(0, 0);
            totalTemplate.showText(String.valueOf(writer.getPageNumber() - 1));
            totalTemplate.endText();
        }
    }
}
