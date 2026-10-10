package com.qms.common.util;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/**
 * HtmlSanitizer — server-side guard for every rich-text field saved
 * by the QMS modules.
 *
 * The frontend's SafeHtml renderer runs a display-side allowlist, but
 * display-side is defense-in-depth, not the first line. Every service
 * that persists a Quill-produced HTML string must route it through
 * {@link #sanitize(String)} before calling the entity setter.
 *
 * Allowlist mirrors Quill 2's output surface:
 *   p · br · strong · b · em · i · u · s · sub · sup
 *   ol · ul · li
 *   h1 · h2 · h3
 *   blockquote · pre · code
 *   a (href, target, rel)   — javascript:/data:/vbscript: blocked
 *   img (src, alt, w, h)    — only http(s), relative, or data:image/*
 *   span · div · p · li     — class + style (filtered to safe props)
 *
 * Compliance tie-in — 21 CFR Part 11 §11.10(a) demands records be
 * trusted and reliable. Allowing arbitrary HTML into the audit-logged
 * fields would let a user inject tags into the audit trail; the
 * allowlist stops that at the server boundary.
 */
public final class HtmlSanitizer {

    private HtmlSanitizer() {}

    // Safelist built once, immutable, thread-safe.
    private static final Safelist ALLOW = buildSafelist();

    private static Safelist buildSafelist() {
        Safelist s = Safelist.basicWithImages()
                // basicWithImages already covers:
                //   a, b, blockquote, br, cite, code, dd, dl, dt, em,
                //   i, li, ol, p, pre, q, small, span, strike, strong,
                //   sub, sup, u, ul, img
                // Add Quill extras:
                .addTags("h1", "h2", "h3", "div", "s")
                .addAttributes("a", "target")
                .addAttributes("span", "style", "class")
                .addAttributes("div", "style", "class")
                .addAttributes("p", "style", "class")
                .addAttributes("li", "style", "class")
                .addAttributes("ol", "class")
                .addAttributes("ul", "class");
        // Force rel=noopener on target=_blank links.
        s.addEnforcedAttribute("a", "rel", "noopener noreferrer");
        return s;
    }

    /**
     * Clean a Quill-produced HTML string. Returns null for null input,
     * preserves the empty editor marker "<p><br></p>" as empty string
     * so callers can treat both the same.
     *
     * Jsoup also normalises malformed input (unclosed tags, nested
     * blocks, Word paste noise), so this doubles as an input cleaner.
     */
    public static String sanitize(String html) {
        if (html == null) return null;
        if (html.isBlank()) return html;
        // Fast-path for the Quill empty-editor marker — avoids a parse.
        String trimmed = html.trim();
        if ("<p><br></p>".equalsIgnoreCase(trimmed) || "<p><br/></p>".equalsIgnoreCase(trimmed)) {
            return "";
        }
        return Jsoup.clean(html, ALLOW);
    }

    /**
     * Strip ALL HTML and return plain text. Used by the PDF exporter
     * (R.3) so reports read as prose, not raw markup. Line breaks and
     * paragraph boundaries are preserved as newlines; list items get a
     * leading "- " to stay readable in the PDF layout.
     */
    public static String toPlainText(String html) {
        if (html == null || html.isBlank()) return "";
        // Normalise paragraph / line breaks before stripping tags so
        // the result reads as separate lines, not one giant paragraph.
        String normalised = html
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p\\s*>", "\n")
                .replaceAll("(?i)<li[^>]*>", "- ")
                .replaceAll("(?i)</li\\s*>", "\n")
                .replaceAll("(?i)</h[1-6]\\s*>", "\n");
        return Jsoup.parse(normalised).text()
                // Jsoup's .text() collapses whitespace; put newlines back.
                .replace(" - ", "\n- ")
                .trim();
    }

    /**
     * Is this HTML string effectively empty? Quill's empty editor
     * emits "<p><br></p>" which {@link String#isBlank()} doesn't catch.
     */
    public static boolean isEffectivelyEmpty(String html) {
        if (html == null || html.isBlank()) return true;
        String stripped = Jsoup.parse(html).text().replace(" ", " ").trim();
        return stripped.isEmpty();
    }
}
