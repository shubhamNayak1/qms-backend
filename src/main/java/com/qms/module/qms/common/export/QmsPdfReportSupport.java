package com.qms.module.qms.common.export;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;

import java.awt.Color;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Round-N follow-up (2026-07-07): shared PDF-rendering primitives for all
 * QMS per-record report exporters.
 *
 * The Change Control exporter shipped first with these helpers inlined; this
 * class extracts them so CAPA / Deviation / Incident / Market Complaint can
 * ship the same tabular layout without copy-pasting the OpenPDF plumbing.
 *
 * All methods are static — no Spring bean, no state — because OpenPDF's
 * PdfPTable / PdfPCell layer is itself stateful per-document.
 */
@Slf4j
public final class QmsPdfReportSupport {

    public static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    public static final DateTimeFormatter DT_FMT   = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    public static final Font FONT_HEADER  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.BLACK);
    public static final Font FONT_TITLE   = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.BLACK);
    public static final Font FONT_SECTION = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.BLACK);
    public static final Font FONT_LABEL   = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.BLACK);
    public static final Font FONT_BODY    = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.BLACK);

    public static final Color SECTION_BG = new Color(230, 230, 230);
    public static final Color BORDER     = new Color(140, 140, 140);

    private QmsPdfReportSupport() { /* static helpers only */ }

    // ── Table layout ─────────────────────────────────────────

    public static PdfPTable twoColTable() {
        PdfPTable t = new PdfPTable(new float[]{1.4f, 3f});
        t.setWidthPercentage(100);
        t.setSpacingBefore(2);
        return t;
    }

    public static PdfPTable singleColTable() {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        t.setSpacingBefore(2);
        return t;
    }

    public static void sectionBanner(Document doc, String text) throws DocumentException {
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

    // ── Cell helpers ─────────────────────────────────────────

    public static void addKv(PdfPTable t, String label, String value) {
        PdfPCell l = new PdfPCell(new Phrase(label, FONT_LABEL));
        l.setPadding(3); l.setBorderColor(BORDER);
        t.addCell(l);
        PdfPCell v = new PdfPCell(new Phrase(": " + safe(value), FONT_BODY));
        v.setPadding(3); v.setBorderColor(BORDER);
        t.addCell(v);
    }

    public static void addLabeled(PdfPTable t, String label, String value) {
        Phrase p = new Phrase();
        p.add(new Chunk(label + " : ", FONT_LABEL));
        p.add(new Chunk(safe(value), FONT_BODY));
        PdfPCell c = new PdfPCell(p);
        c.setPadding(4); c.setBorderColor(BORDER);
        t.addCell(c);
    }

    public static void addHeader(PdfPTable t, String txt) {
        PdfPCell c = new PdfPCell(new Phrase(txt, FONT_LABEL));
        c.setBackgroundColor(SECTION_BG);
        c.setPadding(4);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setBorderColor(BORDER);
        t.addCell(c);
    }

    public static void addBody(PdfPTable t, String txt) {
        PdfPCell c = new PdfPCell(new Phrase(safe(txt), FONT_BODY));
        c.setPadding(3);
        c.setBorderColor(BORDER);
        t.addCell(c);
    }

    public static void addSpanCell(PdfPTable t, String txt, int span) {
        PdfPCell c = new PdfPCell(new Phrase(txt, FONT_BODY));
        c.setPadding(3);
        c.setColspan(span);
        c.setBorderColor(BORDER);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        t.addCell(c);
    }

    public static PdfPCell cell(String txt, Color bg) {
        PdfPCell c = new PdfPCell(new Phrase(txt, FONT_BODY));
        if (bg != null) c.setBackgroundColor(bg);
        c.setPadding(3);
        c.setBorderColor(BORDER);
        return c;
    }

    // ── Utilities ────────────────────────────────────────────

    public static String safe(String s) {
        return (s == null || s.isBlank()) ? "—" : s;
    }

    public static String yesNo(Boolean b) {
        return Boolean.TRUE.equals(b) ? "Yes" : "No";
    }

    public static void appendIfTrue(StringBuilder sb, Boolean flag, String label) {
        if (Boolean.TRUE.equals(flag)) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(label);
        }
    }

    // ── Page footer ──────────────────────────────────────────

    /**
     * Shared footer stripe: company · record · | · user · timestamp · Page N.
     * BaseFont is resolved once in onOpenDocument via BaseFont.createFont
     * (FontFactory.getFont().getBaseFont() can return null in OpenPDF and
     * NPE on setFontAndSize — see 2026-07-07 fix on the CC exporter).
     */
    public static class PageFooter extends PdfPageEventHelper {
        private final String company;
        private final String recordNumber;
        private final String user;
        private BaseFont     footerFont;

        public PageFooter(String company, String recordNumber, String user) {
            this.company      = company;
            this.recordNumber = recordNumber;
            this.user         = user;
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document doc) {
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
            String left  = safe(company) + " · " + safe(recordNumber);
            String right = safe(user) + " · " + DT_FMT.format(LocalDateTime.now())
                    + " · Page " + writer.getPageNumber();
            PdfContentByte cb = writer.getDirectContent();
            cb.beginText();
            cb.setFontAndSize(footerFont, 7);
            cb.setColorFill(Color.DARK_GRAY);
            cb.showTextAligned(Element.ALIGN_LEFT,  left,  30, 20, 0);
            cb.showTextAligned(Element.ALIGN_RIGHT, right, doc.getPageSize().getWidth() - 30, 20, 0);
            cb.endText();
        }
    }
}
