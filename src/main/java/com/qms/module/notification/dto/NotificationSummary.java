package com.qms.module.notification.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Aggregated notification payload returned by GET /api/v1/notifications.
 * Contains badge counts at the top for quick UI rendering, followed by the
 * full ordered list of notification items.
 */
@Data
@Builder
public class NotificationSummary {

    /** Total number of notifications across all categories. */
    private int totalCount;

    /** Number of CRITICAL severity notifications — drives the red badge. */
    private int criticalCount;

    /** Number of WARNING severity notifications. */
    private int warningCount;

    /** Number of INFO severity notifications. */
    private int infoCount;

    /**
     * Count per category — keys are NotificationCategory names:
     * QMS, DMS, LMS, SYSTEM.
     */
    private Map<String, Integer> countByCategory;

    /**
     * Full list of notifications sorted by:
     *   1. Severity (CRITICAL → WARNING → INFO)
     *   2. Due date ascending (earliest first, null last)
     */
    private List<NotificationItem> notifications;
}
