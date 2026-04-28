package com.qms.module.notification.dto;

/** Top-level module a notification belongs to. */
public enum NotificationCategory {
    QMS,     // CAPA, Deviation, Incident, Change Control, Market Complaint
    DMS,     // Documents — expiry, review, approval
    LMS,     // Training enrollments, certificates, assessment reviews
    SYSTEM   // Password, account alerts
}
