package com.qms.module.user.entity;

import com.qms.common.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * TcdPolicy — one row per QMS module controlling the Target Closure Days
 * (max days from Initiate to Close) and the related overdue / extension
 * configuration.
 *
 * Fields:
 *   moduleKey        — 'CAPA' | 'DEVIATION' | 'INCIDENT' | 'MARKET_COMPLAINT' | 'CHANGE_CONTROL'
 *   maxDays          — hard cap in days from initiation to closure
 *   warningDays      — the dashboard flips rows amber this many days
 *                      before the due date
 *   extensionAllowed — whether a target-date extension can be requested
 *   maxExtensions    — hard cap on extensions per record
 */
@Entity
@Table(name = "tcd_policies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TcdPolicy extends BaseEntity {

    @Column(name = "module_key", nullable = false, unique = true, length = 40)
    private String moduleKey;

    @Column(name = "max_days", nullable = false)
    private int maxDays;

    @Column(name = "warning_days", nullable = false)
    private int warningDays;

    @Column(name = "extension_allowed", nullable = false)
    private boolean extensionAllowed;

    @Column(name = "max_extensions", nullable = false)
    private int maxExtensions;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;
}
