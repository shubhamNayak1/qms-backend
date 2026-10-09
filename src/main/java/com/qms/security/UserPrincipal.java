package com.qms.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.qms.module.user.entity.User;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
@AllArgsConstructor
public class UserPrincipal implements UserDetails {

    private final Long   id;
    private final String username;
    private final String email;
    private final String fullName;

    @JsonIgnore
    private final String password;

    private final boolean enabled;
    private final boolean accountNonLocked;
    private final boolean mustChangePassword;
    private final Collection<? extends GrantedAuthority> authorities;

    // ─── Factory ────────────────────────────────────────────

    public static UserPrincipal create(User user) {
        // Role-granted permissions only. CustomUserDetailsService calls
        // the overload below when it has user_permissions overrides on
        // hand; this one-arg entry point stays for callers that don't.
        Set<String> rolePerms = user.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(p -> p.getName())
                .collect(Collectors.toCollection(HashSet::new));
        return create(user, rolePerms);
    }

    /**
     * Build the principal with the final effective permission set
     * already computed by the caller — i.e. role permissions ∪ grants
     * \ revokes. CustomUserDetailsService resolves user_permissions
     * rows against the permissions table and passes the result in,
     * which keeps this factory free of any DB access.
     */
    public static UserPrincipal create(User user, Set<String> effectivePermissions) {
        Set<GrantedAuthority> roleAuthorities = user.getRoles().stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r.getName()))
                .collect(Collectors.toSet());

        Set<GrantedAuthority> permissionAuthorities =
                (effectivePermissions == null ? Collections.<String>emptySet() : effectivePermissions)
                        .stream()
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toSet());

        Set<GrantedAuthority> allAuthorities = Stream
                .concat(roleAuthorities.stream(), permissionAuthorities.stream())
                .collect(Collectors.toSet());

        return new UserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getPasswordHash(),
                user.getIsActive(),
                !user.isAccountLocked(),
                Boolean.TRUE.equals(user.getMustChangePassword()),
                allAuthorities
        );
    }

    // ─── UserDetails contract ───────────────────────────────

    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }

    // ─── Convenience helpers ────────────────────────────────

    public Set<String> getRoleNames() {
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .collect(Collectors.toSet());
    }

    public Set<String> getPermissionNames() {
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> !a.startsWith("ROLE_"))
                .collect(Collectors.toSet());
    }
}
