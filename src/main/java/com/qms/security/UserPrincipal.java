package com.qms.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.qms.module.user.entity.User;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.Collectors;

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
        // Role authorities — prefixed with ROLE_
        Set<GrantedAuthority> roleAuthorities = user.getRoles().stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r.getName()))
                .collect(Collectors.toSet());

        // Permission authorities — no prefix, used with hasAuthority()
        Set<GrantedAuthority> permissionAuthorities = user.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(p -> new SimpleGrantedAuthority(p.getName()))
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
