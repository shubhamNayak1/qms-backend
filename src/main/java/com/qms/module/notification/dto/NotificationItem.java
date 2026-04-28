package com.qms.module.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A single actionable notification surfaced to the current user.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationItem {

    /** DB primary key of the referenced entity (null for SYSTEM notifications). */
    private Long id;

    /** Human-readable record number — e.g. CAPA-202404-0001, DOC-SOP-202403-0012. */
    private String recordNumber;

    /** Short headline shown in the notification panel. */
    private String title;

    /** Full explanatory message. */
    private String message;

    /** Urgency level — CRITICAL, WARNING or INFO. */
    private NotificationSeverity severity;

    /** Top-level module — QMS, DMS, LMS or SYSTEM. */
    private NotificationCategory category;

    /** Sub-module within the category — e.g. CAPA, DEVIATION, DOCUMENT, ENROLLMENT. */
    private String module;

    /** Current status of the referenced entity (human-readable). */
    private String status;

    /** Priority of the referenced QMS record (null for non-QMS notifications). */
    private String priority;

    /** The date the item is due; null for SYSTEM notifications. */
    private LocalDate dueDate;

    /** True when dueDate is in the past and the item is not yet terminal. */
    private boolean overdue;

    /** One-line prompt telling the user exactly what to do. */
    private String actionRequired;

    /** Frontend route the user should navigate to — e.g. /qms/capa/42. */
    private String link;

    /** When the underlying record was created. */
    private LocalDateTime createdAt;
}
