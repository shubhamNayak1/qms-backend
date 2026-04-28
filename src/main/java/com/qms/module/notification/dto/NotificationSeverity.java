package com.qms.module.notification.dto;

/**
 * Severity levels for notification items.
 * Ordered from most critical to least — ordinal is used for sorting.
 */
public enum NotificationSeverity {

    /** Requires immediate action (overdue, high-priority, password expired, etc.). */
    CRITICAL,

    /** Action needed soon — due within threshold or pending review. */
    WARNING,

    /** Informational — no immediate action but user should be aware. */
    INFO
}
