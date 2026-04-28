package com.qms.module.notification.service;

import com.qms.common.enums.Priority;
import com.qms.common.enums.QmsStatus;
import com.qms.common.exception.AppException;
import com.qms.module.dms.entity.Document;
import com.qms.module.dms.entity.DocumentApproval;
import com.qms.module.dms.repository.DocumentApprovalRepository;
import com.qms.module.dms.repository.DocumentRepository;
import com.qms.module.lms.entity.AssessmentAttempt;
import com.qms.module.lms.entity.Enrollment;
import com.qms.module.lms.entity.TrainingCertificate;
import com.qms.module.lms.repository.AssessmentAttemptRepository;
import com.qms.module.lms.repository.EnrollmentRepository;
import com.qms.module.lms.repository.TrainingCertificateRepository;
import com.qms.module.notification.dto.NotificationCategory;
import com.qms.module.notification.dto.NotificationItem;
import com.qms.module.notification.dto.NotificationSeverity;
import com.qms.module.notification.dto.NotificationSummary;
import com.qms.module.qms.capa.repository.CapaRepository;
import com.qms.module.qms.changecontrol.repository.ChangeControlRepository;
import com.qms.module.qms.common.entity.QmsRecord;
import com.qms.module.qms.complaint.repository.MarketComplaintRepository;
import com.qms.module.qms.deviation.repository.DeviationRepository;
import com.qms.module.qms.incident.repository.IncidentRepository;
import com.qms.module.user.entity.Role;
import com.qms.module.user.entity.User;
import com.qms.module.user.repository.UserRepository;
import com.qms.module.user.service.PasswordPolicyService;
import com.qms.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    // ── Repositories ──────────────────────────────────────────

    private final UserRepository               userRepository;
    private final PasswordPolicyService        passwordPolicyService;

    // QMS
    private final CapaRepository               capaRepository;
    private final DeviationRepository          deviationRepository;
    private final IncidentRepository           incidentRepository;
    private final ChangeControlRepository      changeControlRepository;
    private final MarketComplaintRepository    marketComplaintRepository;

    // DMS
    private final DocumentRepository           documentRepository;
    private final DocumentApprovalRepository   documentApprovalRepository;

    // LMS
    private final EnrollmentRepository         enrollmentRepository;
    private final TrainingCertificateRepository certificateRepository;
    private final AssessmentAttemptRepository  attemptRepository;

    // ── Thresholds ────────────────────────────────────────────

    private static final int DUE_SOON_DAYS           = 7;   // QMS / LMS due-soon window
    private static final int CERT_EXPIRY_WARN_DAYS   = 30;  // cert expiry warning
    private static final int DOC_EXPIRY_WARN_DAYS    = 30;  // document expiry warning
    private static final int DOC_REVIEW_WARN_DAYS    = 30;  // document review-due warning
    private static final int PWD_EXPIRY_WARN_DAYS    = 7;   // password expiry warning

    /** Statuses that require a manager / QA-officer to act (approval queue). */
    private static final List<QmsStatus> MANAGER_APPROVAL_STATUSES = List.of(
            QmsStatus.PENDING_HEAD_QA,
            QmsStatus.PENDING_HOD,
            QmsStatus.PENDING_SITE_HEAD
    );

    private static final List<QmsStatus> QA_REVIEW_STATUSES = List.of(
            QmsStatus.PENDING_QA_REVIEW,
            QmsStatus.PENDING_DEPT_COMMENT,
            QmsStatus.PENDING_RA_REVIEW
    );

    // ─────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────

    public NotificationSummary getNotificationsForCurrentUser() {
        Long userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> AppException.unauthorized("Not authenticated"));

        User user = userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> AppException.notFound("User", userId));

        Set<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());

        boolean isManager   = roles.contains("SUPER_ADMIN") || roles.contains("QA_MANAGER");
        boolean isQaOfficer = roles.contains("QA_OFFICER");
        boolean isAuditor   = roles.contains("AUDITOR");

        LocalDate today = LocalDate.now();

        List<NotificationItem> all = new ArrayList<>();
        all.addAll(buildSystemNotifications(user, today));
        all.addAll(buildQmsNotifications(userId, isManager, isQaOfficer, today));
        all.addAll(buildDmsNotifications(userId, isManager || isQaOfficer || isAuditor, today));
        all.addAll(buildLmsNotifications(userId, isManager, today));

        // Sort: CRITICAL → WARNING → INFO, then earliest dueDate first (null last)
        all.sort(Comparator
                .comparingInt((NotificationItem n) -> n.getSeverity().ordinal())
                .thenComparing(n -> n.getDueDate() != null ? n.getDueDate() : LocalDate.MAX));

        // Aggregate counts
        Map<String, Integer> countByCategory = all.stream()
                .collect(Collectors.groupingBy(
                        n -> n.getCategory().name(),
                        Collectors.summingInt(n -> 1)));

        long critical = all.stream().filter(n -> n.getSeverity() == NotificationSeverity.CRITICAL).count();
        long warning  = all.stream().filter(n -> n.getSeverity() == NotificationSeverity.WARNING).count();
        long info     = all.stream().filter(n -> n.getSeverity() == NotificationSeverity.INFO).count();

        return NotificationSummary.builder()
                .totalCount(all.size())
                .criticalCount((int) critical)
                .warningCount((int) warning)
                .infoCount((int) info)
                .countByCategory(countByCategory)
                .notifications(all)
                .build();
    }

    // ─────────────────────────────────────────────────────────
    // SYSTEM notifications
    // ─────────────────────────────────────────────────────────

    private List<NotificationItem> buildSystemNotifications(User user, LocalDate today) {
        List<NotificationItem> items = new ArrayList<>();

        // 1 — Must change password (forced by admin or first login)
        if (Boolean.TRUE.equals(user.getMustChangePassword())) {
            items.add(NotificationItem.builder()
                    .title("Password Change Required")
                    .message("Your password must be changed before you can continue using the system.")
                    .severity(NotificationSeverity.CRITICAL)
                    .category(NotificationCategory.SYSTEM)
                    .module("PASSWORD")
                    .actionRequired("Change your password immediately")
                    .link("/profile/change-password")
                    .createdAt(LocalDateTime.now())
                    .build());
        }

        // 2 — Password expiring soon (based on active policy)
        int validPeriod = passwordPolicyService.getActiveValidPeriod();
        if (validPeriod > 0 && user.getPasswordChangedAt() != null) {
            LocalDate expiryDate = user.getPasswordChangedAt().toLocalDate().plusDays(validPeriod);
            long daysLeft = today.until(expiryDate, ChronoUnit.DAYS);

            if (daysLeft >= 0 && daysLeft <= PWD_EXPIRY_WARN_DAYS) {
                items.add(NotificationItem.builder()
                        .title("Password Expiring Soon")
                        .message("Your password will expire in " + daysLeft + " day(s) on " + expiryDate + ". "
                                + "Please change it before it expires to avoid being locked out.")
                        .severity(NotificationSeverity.WARNING)
                        .category(NotificationCategory.SYSTEM)
                        .module("PASSWORD")
                        .dueDate(expiryDate)
                        .overdue(false)
                        .actionRequired("Change your password before " + expiryDate)
                        .link("/profile/change-password")
                        .createdAt(LocalDateTime.now())
                        .build());
            }
        }

        return items;
    }

    // ─────────────────────────────────────────────────────────
    // QMS notifications
    // ─────────────────────────────────────────────────────────

    private List<NotificationItem> buildQmsNotifications(Long userId,
                                                          boolean isManager,
                                                          boolean isQaOfficer,
                                                          LocalDate today) {
        List<NotificationItem> items = new ArrayList<>();

        // 1 — Records assigned to (or raised by and rejected for) the current user
        Stream.of(
                capaRepository.findActiveForUser(userId),
                deviationRepository.findActiveForUser(userId),
                incidentRepository.findActiveForUser(userId),
                changeControlRepository.findActiveForUser(userId),
                marketComplaintRepository.findActiveForUser(userId)
        ).flatMap(List::stream).forEach(r -> {
            String module   = r.getRecordType().name();
            String baseLink = resolveQmsLink(module);
            items.add(buildQmsItem(r, module, baseLink, today));
        });

        // 2 — Manager: all records pending final approval (PENDING_HEAD_QA, PENDING_HOD, PENDING_SITE_HEAD)
        if (isManager) {
            Set<Long> alreadyShown = items.stream()
                    .filter(n -> n.getId() != null)
                    .map(NotificationItem::getId)
                    .collect(Collectors.toSet());

            Stream.of(
                    capaRepository.findByStatusIn(MANAGER_APPROVAL_STATUSES),
                    deviationRepository.findByStatusIn(MANAGER_APPROVAL_STATUSES),
                    incidentRepository.findByStatusIn(MANAGER_APPROVAL_STATUSES),
                    changeControlRepository.findByStatusIn(MANAGER_APPROVAL_STATUSES),
                    marketComplaintRepository.findByStatusIn(MANAGER_APPROVAL_STATUSES)
            ).flatMap(List::stream)
             .filter(r -> !alreadyShown.contains(r.getId()))  // no duplicates
             .forEach(r -> {
                 String module   = r.getRecordType().name();
                 String baseLink = resolveQmsLink(module);
                 items.add(buildApprovalItem(r, module, baseLink, today));
             });
        }

        // 3 — QA Officer: records pending QA review
        if (isQaOfficer && !isManager) {
            Set<Long> alreadyShown = items.stream()
                    .filter(n -> n.getId() != null)
                    .map(NotificationItem::getId)
                    .collect(Collectors.toSet());

            Stream.of(
                    capaRepository.findByStatusIn(QA_REVIEW_STATUSES),
                    deviationRepository.findByStatusIn(QA_REVIEW_STATUSES),
                    incidentRepository.findByStatusIn(QA_REVIEW_STATUSES),
                    changeControlRepository.findByStatusIn(QA_REVIEW_STATUSES),
                    marketComplaintRepository.findByStatusIn(QA_REVIEW_STATUSES)
            ).flatMap(List::stream)
             .filter(r -> !alreadyShown.contains(r.getId()))
             .forEach(r -> {
                 String module   = r.getRecordType().name();
                 String baseLink = resolveQmsLink(module);
                 items.add(buildApprovalItem(r, module, baseLink, today));
             });
        }

        return items;
    }

    private <T extends QmsRecord> NotificationItem buildQmsItem(T r, String module,
                                                                  String baseLink, LocalDate today) {
        boolean overdue = r.getDueDate() != null && r.getDueDate().isBefore(today);
        boolean rejected = r.getStatus() == QmsStatus.REJECTED;

        NotificationSeverity severity;
        String actionRequired;

        if (rejected) {
            severity      = NotificationSeverity.WARNING;
            actionRequired = "Record was rejected — review comments and resubmit";
        } else if (overdue) {
            severity      = NotificationSeverity.CRITICAL;
            actionRequired = "This record is overdue — take action immediately";
        } else if (r.getPriority() == Priority.CRITICAL) {
            severity      = NotificationSeverity.CRITICAL;
            actionRequired = "Critical priority — immediate action required";
        } else if (r.getPriority() == Priority.HIGH) {
            severity      = NotificationSeverity.WARNING;
            actionRequired = "High priority record requires your attention";
        } else {
            severity      = NotificationSeverity.WARNING;
            actionRequired = formatActionForStatus(r.getStatus());
        }

        String msg = rejected
                ? module.replace("_", " ") + " " + r.getRecordNumber() + " was REJECTED — review the comments and resubmit."
                : module.replace("_", " ") + " " + r.getRecordNumber() + " is " + humanStatus(r.getStatus())
                        + (overdue ? " and is OVERDUE" : "") + ".";

        return NotificationItem.builder()
                .id(r.getId())
                .recordNumber(r.getRecordNumber())
                .title(r.getTitle())
                .message(msg)
                .severity(severity)
                .category(NotificationCategory.QMS)
                .module(module)
                .status(r.getStatus().name())
                .priority(r.getPriority() != null ? r.getPriority().name() : null)
                .dueDate(r.getDueDate())
                .overdue(overdue)
                .actionRequired(actionRequired)
                .link(baseLink + "/" + r.getId())
                .createdAt(r.getCreatedAt())
                .build();
    }

    private <T extends QmsRecord> NotificationItem buildApprovalItem(T r, String module,
                                                                       String baseLink, LocalDate today) {
        boolean overdue = r.getDueDate() != null && r.getDueDate().isBefore(today);
        return NotificationItem.builder()
                .id(r.getId())
                .recordNumber(r.getRecordNumber())
                .title(r.getTitle())
                .message(module.replace("_", " ") + " " + r.getRecordNumber()
                        + " is " + humanStatus(r.getStatus()) + " — pending your approval.")
                .severity(overdue ? NotificationSeverity.CRITICAL : NotificationSeverity.WARNING)
                .category(NotificationCategory.QMS)
                .module(module)
                .status(r.getStatus().name())
                .priority(r.getPriority() != null ? r.getPriority().name() : null)
                .dueDate(r.getDueDate())
                .overdue(overdue)
                .actionRequired("Review and approve or reject this "
                        + module.toLowerCase().replace("_", " "))
                .link(baseLink + "/" + r.getId())
                .createdAt(r.getCreatedAt())
                .build();
    }

    // ─────────────────────────────────────────────────────────
    // DMS notifications
    // ─────────────────────────────────────────────────────────

    private List<NotificationItem> buildDmsNotifications(Long userId,
                                                          boolean canReviewDocuments,
                                                          LocalDate today) {
        List<NotificationItem> items = new ArrayList<>();
        LocalDate warnDate = today.plusDays(DOC_EXPIRY_WARN_DAYS);

        // 1 — Documents I own that are expiring soon
        documentRepository.findExpiringSoonForOwner(userId, today, warnDate).forEach(doc -> {
            long daysLeft = today.until(doc.getExpiryDate(), ChronoUnit.DAYS);
            items.add(NotificationItem.builder()
                    .id(doc.getId())
                    .recordNumber(doc.getDocNumber())
                    .title(doc.getTitle())
                    .message("Document " + doc.getDocNumber() + " (v" + doc.getVersion()
                            + ") will expire in " + daysLeft + " day(s) on " + doc.getExpiryDate() + ".")
                    .severity(daysLeft <= 7 ? NotificationSeverity.CRITICAL : NotificationSeverity.WARNING)
                    .category(NotificationCategory.DMS)
                    .module("DOCUMENT")
                    .status("EFFECTIVE")
                    .dueDate(doc.getExpiryDate())
                    .overdue(false)
                    .actionRequired("Review and renew or supersede the document before it expires")
                    .link("/dms/documents/" + doc.getId())
                    .createdAt(doc.getCreatedAt())
                    .build());
        });

        // 2 — Documents I own that are due for periodic review
        LocalDate reviewWarnDate = today.plusDays(DOC_REVIEW_WARN_DAYS);
        documentRepository.findDueForReviewForOwner(userId, today, reviewWarnDate).forEach(doc -> {
            long daysLeft = today.until(doc.getReviewDate(), ChronoUnit.DAYS);
            items.add(NotificationItem.builder()
                    .id(doc.getId())
                    .recordNumber(doc.getDocNumber())
                    .title(doc.getTitle())
                    .message("Document " + doc.getDocNumber() + " is due for its periodic review in "
                            + daysLeft + " day(s) on " + doc.getReviewDate() + ".")
                    .severity(NotificationSeverity.WARNING)
                    .category(NotificationCategory.DMS)
                    .module("DOCUMENT")
                    .status("EFFECTIVE")
                    .dueDate(doc.getReviewDate())
                    .overdue(false)
                    .actionRequired("Review the document and revise or reapprove as necessary")
                    .link("/dms/documents/" + doc.getId())
                    .createdAt(doc.getCreatedAt())
                    .build());
        });

        // 3 — Documents pending my approval (UNDER_REVIEW where I am an approver)
        documentApprovalRepository.findPendingByApprover(userId).forEach(approval -> {
            Document doc = approval.getDocument();
            items.add(NotificationItem.builder()
                    .id(doc.getId())
                    .recordNumber(doc.getDocNumber())
                    .title(doc.getTitle())
                    .message("Document " + doc.getDocNumber() + " (v" + doc.getVersion()
                            + ") is awaiting your approval decision.")
                    .severity(NotificationSeverity.WARNING)
                    .category(NotificationCategory.DMS)
                    .module("DOCUMENT_APPROVAL")
                    .status("UNDER_REVIEW")
                    .actionRequired("Review the document and submit your approval or rejection")
                    .link("/dms/documents/" + doc.getId())
                    .createdAt(approval.getCreatedAt())
                    .build());
        });

        // 4 — Assessments pending manual review (for QA managers / trainers)
        if (canReviewDocuments) {
            attemptRepository.findPendingManualReview().forEach(attempt -> {
                Enrollment enr = attempt.getEnrollment();
                items.add(NotificationItem.builder()
                        .id(attempt.getId())
                        .title("Assessment Pending Review: " + enr.getProgram().getTitle())
                        .message(enr.getUserName() + "'s assessment for '"
                                + enr.getProgram().getTitle()
                                + "' requires manual review (submitted: "
                                + (attempt.getSubmittedAt() != null
                                        ? attempt.getSubmittedAt().toLocalDate() : "N/A") + ").")
                        .severity(NotificationSeverity.WARNING)
                        .category(NotificationCategory.LMS)
                        .module("ASSESSMENT_REVIEW")
                        .status("PENDING_REVIEW")
                        .actionRequired("Review the assessment answers and record a pass/fail decision")
                        .link("/lms/assessments/review/" + attempt.getId())
                        .createdAt(attempt.getSubmittedAt())
                        .build());
            });
        }

        return items;
    }

    // ─────────────────────────────────────────────────────────
    // LMS notifications
    // ─────────────────────────────────────────────────────────

    private List<NotificationItem> buildLmsNotifications(Long userId,
                                                          boolean isManager,
                                                          LocalDate today) {
        List<NotificationItem> items = new ArrayList<>();
        LocalDate dueSoonDate = today.plusDays(DUE_SOON_DAYS);
        LocalDate certWarnDate = today.plusDays(CERT_EXPIRY_WARN_DAYS);

        // 1 — Overdue enrollments
        enrollmentRepository.findOverdueForUser(userId, today).forEach(enr -> {
            long daysOverdue = enr.getDueDate().until(today, ChronoUnit.DAYS);
            items.add(NotificationItem.builder()
                    .id(enr.getId())
                    .title("Training Overdue: " + enr.getProgram().getTitle())
                    .message("Your enrollment in '" + enr.getProgram().getTitle()
                            + "' is " + daysOverdue + " day(s) overdue (was due: " + enr.getDueDate() + ").")
                    .severity(NotificationSeverity.CRITICAL)
                    .category(NotificationCategory.LMS)
                    .module("ENROLLMENT")
                    .status(enr.getStatus().name())
                    .dueDate(enr.getDueDate())
                    .overdue(true)
                    .actionRequired("Complete the training immediately to maintain compliance")
                    .link("/lms/enrollments/" + enr.getId())
                    .createdAt(enr.getCreatedAt())
                    .build());
        });

        // 2 — Enrollments due soon (within DUE_SOON_DAYS)
        enrollmentRepository.findDueSoonForUser(userId, today, dueSoonDate).forEach(enr -> {
            long daysLeft = today.until(enr.getDueDate(), ChronoUnit.DAYS);
            items.add(NotificationItem.builder()
                    .id(enr.getId())
                    .title("Training Due Soon: " + enr.getProgram().getTitle())
                    .message("Your enrollment in '" + enr.getProgram().getTitle()
                            + "' is due in " + daysLeft + " day(s) on " + enr.getDueDate() + ".")
                    .severity(NotificationSeverity.WARNING)
                    .category(NotificationCategory.LMS)
                    .module("ENROLLMENT")
                    .status(enr.getStatus().name())
                    .dueDate(enr.getDueDate())
                    .overdue(false)
                    .actionRequired("Complete the training before the due date to avoid non-compliance")
                    .link("/lms/enrollments/" + enr.getId())
                    .createdAt(enr.getCreatedAt())
                    .build());
        });

        // 3 — Training certificates expiring soon
        certificateRepository.findExpiringSoonForUser(userId, today, certWarnDate).forEach(cert -> {
            long daysLeft = today.until(cert.getExpiryDate(), ChronoUnit.DAYS);
            items.add(NotificationItem.builder()
                    .id(cert.getId())
                    .recordNumber(cert.getCertificateNumber())
                    .title("Certificate Expiring: " + cert.getProgramTitle())
                    .message("Your training certificate for '" + cert.getProgramTitle()
                            + "' (Cert# " + cert.getCertificateNumber()
                            + ") expires in " + daysLeft + " day(s) on " + cert.getExpiryDate() + ".")
                    .severity(daysLeft <= 7 ? NotificationSeverity.CRITICAL : NotificationSeverity.WARNING)
                    .category(NotificationCategory.LMS)
                    .module("CERTIFICATE")
                    .status("ACTIVE")
                    .dueDate(cert.getExpiryDate())
                    .overdue(false)
                    .actionRequired("Re-enroll and complete the training before the certificate expires")
                    .link("/lms/certificates/" + cert.getId())
                    .createdAt(cert.getIssuedDate() != null ? cert.getIssuedDate().atStartOfDay() : null)
                    .build());
        });

        // 4 — Training certificates that have already expired
        certificateRepository.findExpiredForUser(userId, today).forEach(cert -> {
            items.add(NotificationItem.builder()
                    .id(cert.getId())
                    .recordNumber(cert.getCertificateNumber())
                    .title("Certificate Expired: " + cert.getProgramTitle())
                    .message("Your training certificate for '" + cert.getProgramTitle()
                            + "' expired on " + cert.getExpiryDate()
                            + ". Re-enroll to restore compliance.")
                    .severity(NotificationSeverity.CRITICAL)
                    .category(NotificationCategory.LMS)
                    .module("CERTIFICATE")
                    .status("EXPIRED")
                    .dueDate(cert.getExpiryDate())
                    .overdue(true)
                    .actionRequired("Re-enroll immediately — this certificate has expired")
                    .link("/lms/certificates/" + cert.getId())
                    .createdAt(cert.getIssuedDate() != null ? cert.getIssuedDate().atStartOfDay() : null)
                    .build());
        });

        return items;
    }

    // ─────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────

    private String resolveQmsLink(String module) {
        return switch (module) {
            case "CAPA"             -> "/qms/capa";
            case "DEVIATION"        -> "/qms/deviation";
            case "INCIDENT"         -> "/qms/incident";
            case "CHANGE_CONTROL"   -> "/qms/change-control";
            case "MARKET_COMPLAINT" -> "/qms/market-complaint";
            default                 -> "/qms";
        };
    }

    /** Maps a QmsStatus to a short human-readable label. */
    private String humanStatus(QmsStatus status) {
        return switch (status) {
            case DRAFT                   -> "Draft";
            case PENDING_HOD             -> "Pending HOD Approval";
            case PENDING_QA_REVIEW       -> "Pending QA Review";
            case PENDING_DEPT_COMMENT    -> "Pending Department Comment";
            case PENDING_RA_REVIEW       -> "Pending RA Review";
            case PENDING_SITE_HEAD       -> "Pending Site Head Approval";
            case PENDING_CUSTOMER_COMMENT-> "Pending Customer Comment";
            case PENDING_HEAD_QA         -> "Pending Head of QA Approval";
            case PENDING_INVESTIGATION   -> "Under Investigation";
            case PENDING_ATTACHMENTS     -> "Awaiting Attachments";
            case PENDING_VERIFICATION    -> "Pending Verification";
            case REJECTED                -> "Rejected";
            case CLOSED                  -> "Closed";
            case CANCELLED               -> "Cancelled";
            case REOPENED                -> "Reopened";
        };
    }

    /** Returns a concise action prompt for a given QMS workflow status. */
    private String formatActionForStatus(QmsStatus status) {
        return switch (status) {
            case DRAFT                   -> "Complete and submit the record for review";
            case PENDING_HOD             -> "Awaiting Head of Department approval";
            case PENDING_QA_REVIEW       -> "Awaiting QA team review";
            case PENDING_DEPT_COMMENT    -> "Provide department comment/feedback";
            case PENDING_RA_REVIEW       -> "Awaiting Regulatory Affairs review";
            case PENDING_SITE_HEAD       -> "Awaiting Site Head approval";
            case PENDING_CUSTOMER_COMMENT-> "Awaiting customer comment";
            case PENDING_HEAD_QA         -> "Awaiting Head of QA final decision";
            case PENDING_INVESTIGATION   -> "Complete the investigation and update findings";
            case PENDING_ATTACHMENTS     -> "Upload the required attachments or lab results";
            case PENDING_VERIFICATION    -> "Verify and confirm the corrective actions";
            default                      -> "Review and take appropriate action";
        };
    }
}
