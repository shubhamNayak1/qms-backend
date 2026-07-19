package com.qms.module.qms.changecontrol.export;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.qms.common.enums.QmsRecordType;
import com.qms.common.enums.QmsStatus;
import com.qms.common.exception.AppException;
import com.qms.module.qms.changecontrol.dto.response.ChangeControlResponse;
import com.qms.module.qms.common.dto.response.QmsDepartmentActionItemResponse;
import com.qms.module.qms.common.dto.response.QmsDepartmentCommentResponse;
import com.qms.module.qms.common.dto.response.QmsLineItemResponse;
import com.qms.module.qms.common.export.QmsPdfReportSupport;
import com.qms.module.qms.common.repository.QmsRecordAttachmentRepository;
import com.qms.module.qms.common.service.QmsDepartmentActionItemService;
import com.qms.module.qms.common.service.QmsDepartmentCommentService;
import com.qms.module.qms.common.service.QmsLineItemService;
import com.qms.module.qms.common.workflow.StatusHistoryEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.qms.module.qms.common.export.QmsPdfReportSupport.DATE_FMT;
import static com.qms.module.qms.common.export.QmsPdfReportSupport.DT_FMT;
import static com.qms.module.qms.common.export.QmsPdfReportSupport.FONT_HEADER;
import static com.qms.module.qms.common.export.QmsPdfReportSupport.FONT_SECTION;
import static com.qms.module.qms.common.export.QmsPdfReportSupport.FONT_TITLE;
import static com.qms.module.qms.common.export.QmsPdfReportSupport.FONT_BODY;
import static com.qms.module.qms.common.export.QmsPdfReportSupport.addBody;
import static com.qms.module.qms.common.export.QmsPdfReportSupport.addHeader;
import static com.qms.module.qms.common.export.QmsPdfReportSupport.addKv;
import static com.qms.module.qms.common.export.QmsPdfReportSupport.addLabeled;
import static com.qms.module.qms.common.export.QmsPdfReportSupport.addSpanCell;
import static com.qms.module.qms.common.export.QmsPdfReportSupport.appendIfTrue;
import static com.qms.module.qms.common.export.QmsPdfReportSupport.cell;
import static com.qms.module.qms.common.export.QmsPdfReportSupport.safe;
import static com.qms.module.qms.common.export.QmsPdfReportSupport.sectionBanner;
import static com.qms.module.qms.common.export.QmsPdfReportSupport.singleColTable;
import static com.qms.module.qms.common.export.QmsPdfReportSupport.twoColTable;
import static com.qms.module.qms.common.export.QmsPdfReportSupport.yesNo;

/**
 * Round-N (2026-07-04) tester CC-Point-2 · new "Report" ask.
 * Round-N follow-up (2026-07-07): primitives moved to QmsPdfReportSupport
 * so CAPA / Deviation / Incident / Market Complaint can share the layout.
 *
 * Round-N bug fix (2026-07-07 pm): tester reported the PDF was showing
 * data as though downstream steps had happened when they had not — e.g.
 * "Site Head: Concurrence By Site Head = &lt;QA's Post-Remark&gt;" and
 * "Approval By Head QA: Category = Critical" even though Head QA had
 * not clicked Approve. Root cause: the section guards were "field is
 * populated" (siteHeadRequired = Yes) rather than "the responsible actor
 * has actually completed the step". Fixed by adding stage-guard checks
 * that consult the status_history log for a matching transition, and
 * sourcing per-stage comments from that same log instead of the shared
 * cc.comments slot.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChangeControlPdfReportService {

    private final QmsLineItemService              lineItemService;
    private final QmsDepartmentCommentService     deptCommentService;
    private final QmsDepartmentActionItemService  actionItemService;
    private final QmsRecordAttachmentRepository   attachmentRepository;

    @Value("${reports.export.company-name:QMS Organisation}")
    private String companyName;

    @Value("${reports.export.company-location:Pune}")
    private String companyLocation;

    public byte[] render(ChangeControlResponse cc) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 30, 30, 40, 40);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new QmsPdfReportSupport.PageFooter(
                    companyName, cc.getRecordNumber(), cc.getRaisedByName()));
            doc.open();

            renderCoverHeader(doc, cc);
            renderInitiation(doc, cc);
            renderPeerReview(doc, cc);          // S1 + S7 — was rolled into HOD before
            renderHodAssessment(doc, cc);       // S1 — banner renamed "Review" → "HOD Assessment"
            renderQaEvaluation(doc, cc);
            renderDepartmentComments(doc, cc);
            // RED-2 — Site Head / RA / Customer render in the order they actually
            // occurred, not the fixed sequence I was using before.
            renderPostQaStagesChronologically(doc, cc);
            renderHeadQaApproval(doc, cc);
            renderExtensionRequest(doc, cc);    // RED-4 — extension raised by / decided
            renderVerification(doc, cc);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("CC PDF generation failed for {}: {}",
                    cc != null ? cc.getRecordNumber() : "?", e.getMessage(), e);
            throw AppException.internalError("PDF generation failed: " + e.getMessage());
        }
    }

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

        // S6 — Number-of-attachments alongside the record number in the header.
        long attachmentCount = attachmentRepository
                .findAllByRecordTypeAndRecordIdAndIsDeletedFalseOrderByUploadedAtAsc(
                        QmsRecordType.CHANGE_CONTROL, cc.getId())
                .size();
        PdfPTable no = new PdfPTable(1);
        no.setWidthPercentage(100);
        no.addCell(cell("Change Control No. : " + safe(cc.getRecordNumber())
                + "        Number of Attachment : " + attachmentCount
                + "        Current Status : " + humanStatus(cc.getStatus()), null));
        doc.add(no);
    }

    /**
     * S1 + S7 — Peer Review is a distinct step in the reference layout, not a
     * sub-block of HOD Assessment. Rendered only once peer review actually
     * happened (record left PENDING_REVIEW). Reviewer name, timestamp, and
     * remark come from the transition entry that took the record out.
     */
    private void renderPeerReview(Document doc, ChangeControlResponse cc) throws DocumentException {
        Optional<StatusHistoryEntry> pr = findTransition(cc, QmsStatus.PENDING_REVIEW);
        if (pr.isEmpty()) return;
        sectionBanner(doc, "Review");
        PdfPTable t = singleColTable();
        addLabeled(t, "Peer Review", safe(pr.get().getComment()));
        addLabeled(t, "Review By",
                safe(pr.get().getChangedByUsername())
                        + (pr.get().getChangedAt() != null
                            ? "  ·  " + DT_FMT.format(pr.get().getChangedAt()) : ""));
        doc.add(t);
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
            addKv(t, "Market", safe(cc.getMarketDetails()));
        }
        doc.add(t);

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
                addBody(li, safe(r.getProposedByName())
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

    /**
     * S1 — Was called "Review" until 2026-07-12 — renamed to match the
     * reference layout the tester supplied. HOD Assessment now also shows
     * the HOD's forward/reject decision at the top of the section (S2).
     */
    private void renderHodAssessment(Document doc, ChangeControlResponse cc) throws DocumentException {
        // Only render once HOD has actually completed the review (record left
        // PENDING_HOD). Otherwise the Impact checkboxes / Initial Risk are
        // undefined and the PDF misleads the reader.
        if (!hasPassed(cc, QmsStatus.PENDING_HOD)) return;

        Optional<StatusHistoryEntry> hodTransition = findTransition(cc, QmsStatus.PENDING_HOD);

        sectionBanner(doc, "HOD Assessment");
        PdfPTable t = singleColTable();

        // S2 — HOD's decision at the top: Approved (forwarded to QA), Sent
        // Back (resent to Initiator), or Rejected. Blank when the transition
        // log doesn't tell us where the record went.
        String hodDecision = hodTransition.map(e -> hodOutcomeLabel(e.getToStatus())).orElse(null);
        if (hodDecision != null) {
            addLabeled(t, "Change Proposal Is", hodDecision);
        }

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
        addLabeled(t, "Initial Assessment", safe(cc.getInitialAssessment()));
        addLabeled(t, "Initial Risk Assessment Required",
                Boolean.TRUE.equals(cc.getInitialRiskAssessmentRequired())
                        ? "Required" : "Not Required");
        if (Boolean.TRUE.equals(cc.getInitialRiskAssessmentRequired())) {
            addLabeled(t, "Initial Risk Assessment",
                    safe(cc.getInitialRiskAssessment()));
        }
        // HOD's remark + name/date come from the transition entry.
        addLabeled(t, "Remark / Justification",
                safe(hodTransition.map(StatusHistoryEntry::getComment).orElse(null)));
        addLabeled(t, "Dept. Head / Designee",
                safe(hodTransition.map(StatusHistoryEntry::getChangedByUsername).orElse(null))
                        + hodTransition.filter(e -> e.getChangedAt() != null)
                            .map(e -> "  ·  " + DT_FMT.format(e.getChangedAt())).orElse(""));
        doc.add(t);
    }

    private void renderQaEvaluation(Document doc, ChangeControlResponse cc) throws DocumentException {
        // Render as soon as QA has captured a pre-remark (Phase 1). Fields that
        // belong to Phase 2 are guarded individually below.
        boolean phase1Started = hasPassed(cc, QmsStatus.PENDING_QA_REVIEW)
                || (cc.getPreRemark() != null && !cc.getPreRemark().isBlank());
        if (!phase1Started) return;

        sectionBanner(doc, "Evaluation By QA");
        PdfPTable t = singleColTable();
        addLabeled(t, "Pre Remark/Justification", safe(cc.getPreRemark()));

        // Phase-2-only rows — only render once Phase 2 has actually finished
        // (record has left PENDING_QA_REVIEW).
        boolean phase2Done = hasLeft(cc, QmsStatus.PENDING_QA_REVIEW);
        if (phase2Done) {
            addLabeled(t, "Post Remark/Justification",
                    findTransitionComment(cc, QmsStatus.PENDING_QA_REVIEW, cc.getComments()));
            if (cc.getRiskAssessment() != null && !cc.getRiskAssessment().isBlank()) {
                addLabeled(t, "Risk Assessment", safe(cc.getRiskAssessment()));
            }
            addLabeled(t, "Site Head Required", yesNo(cc.getSiteHeadRequired()));
            addLabeled(t, "Customer Communication Required",
                    Boolean.TRUE.equals(cc.getCustomerCommunicationRequired())
                            || Boolean.TRUE.equals(cc.getCustomerCommentRequired()) ? "Yes" : "No");
            // Category is set by QA at Phase 2 (Head QA can confirm at approval).
            if (cc.getCategory() != null && !cc.getCategory().isBlank()) {
                addLabeled(t, "Category / Risk Level", safe(cc.getCategory()));
            }
        }
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

    /**
     * RED-2 — Site Head, Customer Rep, and RA can each be Required=Yes/No
     * independently, and any combination is valid. The reference PDF asks
     * that the sections appear in the order the comments were captured, not
     * a fixed Site-Head → Customer → RA order. Each of the three per-stage
     * renderers is a no-op when the stage never ran, so this orchestrator
     * only needs to call them in the right sequence.
     */
    private void renderPostQaStagesChronologically(Document doc, ChangeControlResponse cc)
            throws DocumentException {
        // We render at most three stages here: Site Head, Customer, RA.
        // Sort them into the order they were actually completed by comparing
        // their outgoing-transition timestamps. Each renderer is a no-op when
        // that stage never ran, so it's safe to call all three in a
        // deterministic-but-arbitrary order when no history exists.
        LocalDateTime siteAt = findTransition(cc, QmsStatus.PENDING_SITE_HEAD)
                .map(StatusHistoryEntry::getChangedAt).orElse(null);
        LocalDateTime custAt = findTransition(cc, QmsStatus.PENDING_CUSTOMER_COMMENT)
                .map(StatusHistoryEntry::getChangedAt).orElse(null);
        LocalDateTime raAt   = findTransition(cc, QmsStatus.PENDING_RA_REVIEW)
                .map(StatusHistoryEntry::getChangedAt).orElse(null);
        if (siteAt == null && custAt == null && raAt == null) {
            renderSiteHead(doc, cc);
            renderCustomer(doc, cc);
            renderRegulatoryAffairs(doc, cc);
            return;
        }
        // Order values assigned so the tuple with the smaller (site,cust,ra)
        // triple prints first. Null stages go to the end.
        int siteRank = rank(siteAt), custRank = rank(custAt), raRank = rank(raAt);
        for (int step = 0; step < 3; step++) {
            int lowest = Math.min(siteRank, Math.min(custRank, raRank));
            if (lowest == Integer.MAX_VALUE) break;
            if (siteRank == lowest) { renderSiteHead(doc, cc); siteRank = Integer.MAX_VALUE; }
            else if (custRank == lowest) { renderCustomer(doc, cc); custRank = Integer.MAX_VALUE; }
            else { renderRegulatoryAffairs(doc, cc); raRank = Integer.MAX_VALUE; }
        }
    }

    /** Rank helper for the three-stage chronological sort. Null → Integer.MAX. */
    private static int rank(LocalDateTime at) {
        if (at == null) return Integer.MAX_VALUE;
        // Second-precision fits well inside int for any realistic timestamp.
        return (int) (at.toEpochSecond(java.time.ZoneOffset.UTC) & 0x7FFFFFFF);
    }

    private void renderSiteHead(Document doc, ChangeControlResponse cc) throws DocumentException {
        // ONLY render if Site Head has actually acted — i.e. the record has
        // moved OUT of PENDING_SITE_HEAD. The old code guarded on "required =
        // Yes", which meant this section rendered with QA's Post-Remark
        // leaking through cc.comments — the tester's exact complaint.
        Optional<StatusHistoryEntry> sh = findTransition(cc, QmsStatus.PENDING_SITE_HEAD);
        if (sh.isEmpty()) return;

        sectionBanner(doc, "Site Head");
        PdfPTable t = singleColTable();
        addLabeled(t, "Concurrence By Site Head", safe(sh.get().getComment()));
        addLabeled(t, "Site Head / Designee",
                safe(sh.get().getChangedByUsername())
                        + (sh.get().getChangedAt() != null
                            ? "  ·  " + DT_FMT.format(sh.get().getChangedAt()) : ""));
        doc.add(t);
    }

    private void renderCustomer(Document doc, ChangeControlResponse cc) throws DocumentException {
        // Only render once the Customer step has actually been performed. The
        // representative name alone was populated at QA Phase 2, so keying off
        // that gave false positives.
        Optional<StatusHistoryEntry> cu = findTransition(cc, QmsStatus.PENDING_CUSTOMER_COMMENT);
        boolean hasCustomerComment = cc.getCustomerComment() != null && !cc.getCustomerComment().isBlank();
        if (cu.isEmpty() && !hasCustomerComment) return;

        sectionBanner(doc, "Customer Representative");
        PdfPTable t = singleColTable();
        addLabeled(t, "Customer Representative", safe(cc.getCustomerRepresentative()));
        addLabeled(t, "Comments By Customer",
                hasCustomerComment ? cc.getCustomerComment()
                        : cu.map(StatusHistoryEntry::getComment).map(QmsPdfReportSupport::safe).orElse("—"));
        doc.add(t);
    }

    private void renderRegulatoryAffairs(Document doc, ChangeControlResponse cc) throws DocumentException {
        Optional<StatusHistoryEntry> ra = findTransition(cc, QmsStatus.PENDING_RA_REVIEW);
        boolean hasRef = cc.getRegulatorySubmissionReference() != null
                && !cc.getRegulatorySubmissionReference().isBlank();
        if (ra.isEmpty() && !hasRef) return;

        sectionBanner(doc, "Evaluation By Regulatory Affairs Department");
        PdfPTable t = singleColTable();
        addLabeled(t, "Regulatory Submission Required",
                yesNo(cc.getRegulatorySubmissionRequired()));
        addLabeled(t, "Dossier Details With No.",
                safe(cc.getRegulatorySubmissionReference()));
        if (ra.isPresent()) {
            addLabeled(t, "RA Comment", safe(ra.get().getComment()));
            addLabeled(t, "RA Officer",
                    safe(ra.get().getChangedByUsername())
                            + (ra.get().getChangedAt() != null
                                ? "  ·  " + DT_FMT.format(ra.get().getChangedAt()) : ""));
        }
        doc.add(t);
    }

    private void renderHeadQaApproval(Document doc, ChangeControlResponse cc) throws DocumentException {
        // Head QA has to actually approve for this section to exist. Guarding on
        // approvedAt is stricter than approvedByName — a name can be pre-set,
        // but the timestamp is only stamped by the approve action.
        if (cc.getApprovedAt() == null) return;

        sectionBanner(doc, "Approval By Head QA");
        PdfPTable t = singleColTable();
        addLabeled(t, "Category / Risk Level",
                safe(cc.getRiskLevel() != null ? cc.getRiskLevel() : cc.getCategory()));
        addLabeled(t, "Approval Comment", safe(cc.getApprovalComments()));
        // "Change Proposal Is" only makes sense as a decision — Approved when
        // the record has closed or moved into Implementation, Rejected when
        // rejected. In-flight statuses do not belong here.
        String decision = terminalDecision(cc.getStatus());
        if (decision != null) {
            addLabeled(t, "Change Proposal Is", decision);
        }
        addLabeled(t, "Head QA / Designee", safe(cc.getApprovedByName())
                + "  ·  " + DT_FMT.format(cc.getApprovedAt()));
        doc.add(t);
    }

    /**
     * RED-4 — Extension of the closure target date. Rendered whenever any of
     * the extension fields on the record are populated. Fields all live on
     * QmsBaseResponse (targetDateExtension*).
     */
    private void renderExtensionRequest(Document doc, ChangeControlResponse cc)
            throws DocumentException {
        boolean any = cc.getTargetDateExtensionDate() != null
                || (cc.getTargetDateExtensionReason() != null && !cc.getTargetDateExtensionReason().isBlank())
                || (cc.getTargetDateExtensionStatus() != null && !cc.getTargetDateExtensionStatus().isBlank())
                || cc.getTargetDateExtensionRequestedAt() != null
                || cc.getTargetDateExtensionDecidedAt() != null;
        if (!any) return;

        sectionBanner(doc, "Closure Target Date — Extension");
        PdfPTable t = singleColTable();
        if (cc.getTargetCompletionDate() != null) {
            addLabeled(t, "Original Closure Target Date",
                    DATE_FMT.format(cc.getTargetCompletionDate()));
        }
        if (cc.getTargetDateExtensionDate() != null) {
            addLabeled(t, "Requested New Target Date",
                    DATE_FMT.format(cc.getTargetDateExtensionDate()));
        }
        addLabeled(t, "Reason", safe(cc.getTargetDateExtensionReason()));
        addLabeled(t, "Status", safe(cc.getTargetDateExtensionStatus()));
        if (cc.getTargetDateExtensionRequestedAt() != null) {
            addLabeled(t, "Requested On",
                    DT_FMT.format(cc.getTargetDateExtensionRequestedAt()));
        }
        if (cc.getTargetDateExtensionDecidedAt() != null) {
            addLabeled(t, "Decided On",
                    DT_FMT.format(cc.getTargetDateExtensionDecidedAt()));
        }
        doc.add(t);
    }

    private void renderVerification(Document doc, ChangeControlResponse cc) throws DocumentException {
        boolean populated = (cc.getVerificationActionTaken() != null && !cc.getVerificationActionTaken().isBlank())
                || cc.getVerificationEffectiveOn() != null
                || (cc.getVerificationOtherComments() != null && !cc.getVerificationOtherComments().isBlank())
                || cc.getClosedDate() != null;
        // Also gate on the workflow having actually reached verification.
        if (!populated && !hasReached(cc, QmsStatus.PENDING_VERIFICATION)) return;

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

    // ─── Stage-guard helpers ─────────────────────────────────

    /** Any transition where the record left {@code stage}. */
    private static Optional<StatusHistoryEntry> findTransition(ChangeControlResponse cc, QmsStatus stage) {
        List<StatusHistoryEntry> log = cc.getStatusHistory();
        if (log == null) return Optional.empty();
        return log.stream()
                .filter(e -> stage.equals(e.getFromStatus()))
                .reduce((a, b) -> b); // last matching entry
    }

    /** The record has visited {@code stage} at some point (fromStatus in any entry). */
    private static boolean hasLeft(ChangeControlResponse cc, QmsStatus stage) {
        return findTransition(cc, stage).isPresent();
    }

    /** The record has entered {@code stage} at some point (toStatus in any entry) OR is currently at it. */
    private static boolean hasReached(ChangeControlResponse cc, QmsStatus stage) {
        if (stage.equals(cc.getStatus())) return true;
        List<StatusHistoryEntry> log = cc.getStatusHistory();
        if (log == null) return false;
        return log.stream().anyMatch(e -> stage.equals(e.getToStatus()));
    }

    /** {@link #hasLeft(ChangeControlResponse, QmsStatus)} — clearer name at call sites. */
    private static boolean hasPassed(ChangeControlResponse cc, QmsStatus stage) {
        return hasLeft(cc, stage);
    }

    /**
     * Look up the comment that was captured on the transition FROM
     * {@code stage}. Falls back to {@code fallback} when the record has
     * either never been at that stage or the log is missing. This is how we
     * pull the accurate Site-Head / Customer / QA-Phase-2 remark instead of
     * whatever happens to be in cc.comments right now.
     */
    private static String findTransitionComment(ChangeControlResponse cc, QmsStatus stage, String fallback) {
        return findTransition(cc, stage)
                .map(StatusHistoryEntry::getComment)
                .filter(s -> s != null && !s.isBlank())
                .orElse(safe(fallback));
    }

    private static String humanStatus(QmsStatus s) {
        if (s == null) return "—";
        switch (s) {
            case DRAFT:                     return "Draft";
            case PENDING_REVIEW:            return "Awaiting Peer Review";
            case PENDING_HOD:               return "Awaiting HOD Review";
            case PENDING_QA_REVIEW:         return "Under QA Evaluation";
            case PENDING_DEPT_COMMENT:      return "Awaiting Department Comments";
            case PENDING_RA_REVIEW:         return "Awaiting Regulatory Affairs";
            case PENDING_SITE_HEAD:         return "Awaiting Site Head Concurrence";
            case PENDING_CUSTOMER_COMMENT:  return "Awaiting Customer Comment";
            case PENDING_HEAD_QA:           return "Awaiting Head-QA Approval";
            case PENDING_VERIFICATION:      return "Awaiting Verification";
            case CLOSED:                    return "Closed";
            case REJECTED:                  return "Rejected";
            case CANCELLED:                 return "Cancelled";
            default:                        return s.name();
        }
    }

    /**
     * S2 — Where the HOD sent the record maps to the outcome we print in
     * the HOD Assessment section. Forward-to-QA = Approved; Resend to
     * Initiator = Sent Back; explicit Reject = Rejected. Any other target
     * status yields null so the line is omitted rather than misleading.
     */
    private static String hodOutcomeLabel(QmsStatus toStatus) {
        if (toStatus == null) return null;
        switch (toStatus) {
            case PENDING_QA_REVIEW: return "Approved";
            case DRAFT:             return "Sent Back to Initiator";
            case REJECTED:          return "Rejected";
            default:                return null;
        }
    }

    /** "Approved" only when the record actually cleared approval. */
    private static String terminalDecision(QmsStatus s) {
        if (s == null) return null;
        switch (s) {
            case CLOSED:    return "Approved";
            case REJECTED:  return "Rejected";
            case CANCELLED: return "Cancelled";
            default:        return null; // in-flight: do not render the line
        }
    }
}
