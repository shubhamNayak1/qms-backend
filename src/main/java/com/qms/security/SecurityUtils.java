package com.qms.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Utility for reading the current authenticated principal from the
 * Spring Security context without coupling service classes to Spring Security directly.
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    public static Optional<AuditPrincipal> getCurrentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }

        Object principal = auth.getPrincipal();

        // UserPrincipal is the primary JWT-authenticated user — map to AuditPrincipal
        if (principal instanceof UserPrincipal up) {
            // Round-M (2026-06-27) tester CC-Point-1 · Issue 2: extract
            // the user's role(s) for the audit trail so the "Role"
            // column stops rendering "—". Prefer the domain-object list
            // from getRoleNames(); fall back to walking authorities and
            // stripping the "ROLE_" prefix. Multiple roles joined by comma.
            String role = up.getRoleNames().isEmpty()
                    ? auth.getAuthorities().stream()
                          .map(GrantedAuthority::getAuthority)
                          .filter(a -> a != null && a.startsWith("ROLE_"))
                          .map(a -> a.substring("ROLE_".length()))
                          .collect(Collectors.joining(","))
                    : String.join(",", up.getRoleNames());
            return Optional.of(AuditPrincipal.builder()
                    .id(up.getId())
                    .username(up.getUsername())
                    .fullName(up.getFullName())
                    .role(role.isBlank() ? null : role)
                    .build());
        }

        // Already an AuditPrincipal (e.g. set directly for system/batch operations)
        if (principal instanceof AuditPrincipal ap) {
            return Optional.of(ap);
        }

        // Plain string username — background/system thread
        if (principal instanceof String username && !"anonymousUser".equals(username)) {
            return Optional.of(AuditPrincipal.system(username));
        }

        return Optional.empty();
    }

    public static Optional<String> getCurrentUsername() {
        return getCurrentPrincipal().map(AuditPrincipal::getUsername);
    }

    public static Optional<Long> getCurrentUserId() {
        return getCurrentPrincipal().map(AuditPrincipal::getId);
    }
}
