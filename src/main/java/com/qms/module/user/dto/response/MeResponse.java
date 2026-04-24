package com.qms.module.user.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Response for GET /api/v1/auth/me
 *
 * Designed for frontend consumption:
 *  - permissions grouped by module so UI can easily check
 *    "does user have any QMS permission?" before rendering a page
 *  - permissionSet is a flat set for fast O(1) lookup:
 *    permissions.has('QMS_APPROVE')
 */
@Data
@Builder
public class MeResponse {

    // ── Identity ──────────────────────────────────────────────
    private Long    id;
    private String  username;
    private String  email;
    private String  fullName;
    private String  firstName;
    private String  lastName;
    private String  phone;
    private String  department;
    private String  designation;
    private String  employeeId;
    private String  profilePictureUrl;
    private Boolean isActive;
    private LocalDateTime lastLoginAt;

    /**
     * True when the user must change their password before proceeding
     * (first login after account creation, or after an admin password reset).
     */
    private Boolean mustChangePassword;

    // ── Roles ─────────────────────────────────────────────────
    /** e.g. ["QA_MANAGER", "AUDITOR"] */
    private Set<String> roles;

    // ── Permissions ───────────────────────────────────────────
    /**
     * Flat set of all permission names — use for quick checks:
     *   permissionSet.contains("QMS_APPROVE")
     */
    private Set<String> permissionSet;

    /**
     * Permissions grouped by module — use to show/hide entire pages:
     *   {
     *     "QMS":    ["QMS_VIEW", "QMS_CREATE", "QMS_UPDATE"],
     *     "DMS":    ["DMS_VIEW", "DMS_DOWNLOAD"],
     *     "LMS":    ["LMS_VIEW", "LMS_ASSESS"],
     *     "REPORT": ["REPORT_VIEW"],
     *     "AUDIT":  ["AUDIT_VIEW"]
     *   }
     */
    private Map<String, List<String>> permissionsByModule;

    /**
     * Quick boolean flags per module — use to hide nav items:
     *   { "QMS": true, "DMS": true, "LMS": true, "REPORT": false, "AUDIT": false }
     */
    private Map<String, Boolean> moduleAccess;
}
