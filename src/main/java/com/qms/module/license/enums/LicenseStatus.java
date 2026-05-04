package com.qms.module.license.enums;

/**
 * Lifecycle of a single license.
 *
 * AVAILABLE — newly generated, sitting in the pool, never assigned.
 * ASSIGNED  — currently bound to a specific user and counts toward the
 *             active-license check at login.
 * REVOKED   — admin pulled the license back from the user; the user can no
 *             longer log in (unless re-assigned a different license).
 * EXPIRED   — license carried an expiry date that has passed. Cannot be
 *             reassigned without bumping its expiry.
 */
public enum LicenseStatus {
    AVAILABLE,
    ASSIGNED,
    REVOKED,
    EXPIRED
}
