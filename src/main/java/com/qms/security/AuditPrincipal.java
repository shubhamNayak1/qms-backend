package com.qms.security;

import lombok.Builder;
import lombok.Getter;

/**
 * Thin DTO holding only the audit-relevant fields of the authenticated user.
 * Decouples the audit module from the full UserPrincipal in the user module.
 *
 * Implement this interface in your UserPrincipal class, or adapt via SecurityUtils.
 */
@Getter
@Builder
public class AuditPrincipal {

    private final Long   id;
    private final String username;
    private final String fullName;
    private final String department;

    /** JWT token id (jti claim) — used as sessionId in audit logs. */
    private final String sessionId;

    /** Creates a system-level principal for background operations. */
    public static AuditPrincipal system(String username) {
        return AuditPrincipal.builder()
                .id(0L)
                .username(username)
                .fullName("SYSTEM")
                .build();
    }
}
