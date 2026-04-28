package com.qms.module.notification.controller;

import com.qms.common.response.ApiResponse;
import com.qms.module.notification.dto.NotificationSummary;
import com.qms.module.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Cross-module actionable notifications for the current user")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * Returns all actionable notifications for the currently authenticated user,
     * sorted by severity (CRITICAL → WARNING → INFO) then due date.
     *
     * <p>Role-aware:
     * <ul>
     *   <li>All users — their own assigned QMS records, LMS enrollments/certificates,
     *       documents they own, and system alerts (password expiry etc.)</li>
     *   <li>QA_MANAGER / SUPER_ADMIN — additionally see all records pending final
     *       approval (PENDING_HEAD_QA, PENDING_HOD, PENDING_SITE_HEAD)</li>
     *   <li>QA_OFFICER — additionally see records pending QA review
     *       (PENDING_QA_REVIEW, PENDING_DEPT_COMMENT, PENDING_RA_REVIEW)</li>
     *   <li>QA_MANAGER / QA_OFFICER / AUDITOR — additionally see LMS assessment
     *       attempts that require manual review</li>
     * </ul>
     */
    @GetMapping
    @Operation(summary = "Get all actionable notifications for the current user",
               description = "Returns a role-aware, cross-module notification summary with badge counts "
                           + "and a full sorted list of actionable items.")
    public ResponseEntity<ApiResponse<NotificationSummary>> getNotifications() {
        return ApiResponse.ok(notificationService.getNotificationsForCurrentUser());
    }

    /**
     * Lightweight endpoint for the notification badge — returns only the counts,
     * no notification detail. Call this on every page load to keep the badge fresh.
     */
    @GetMapping("/count")
    @Operation(summary = "Get notification badge counts only (lightweight)",
               description = "Returns total, critical, warning and info counts without the full notification list. "
                           + "Use this for the header badge to avoid fetching the full payload on every page.")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> getCount() {
        NotificationSummary summary = notificationService.getNotificationsForCurrentUser();
        Map<String, Integer> counts = Map.of(
                "total",    summary.getTotalCount(),
                "critical", summary.getCriticalCount(),
                "warning",  summary.getWarningCount(),
                "info",     summary.getInfoCount()
        );
        return ApiResponse.ok(counts);
    }
}
