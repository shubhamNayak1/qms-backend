package com.qms.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

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
            return Optional.of(AuditPrincipal.builder()
                    .id(up.getId())
                    .username(up.getUsername())
                    .fullName(up.getFullName())
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
