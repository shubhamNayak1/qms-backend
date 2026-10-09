package com.qms.security;

import com.qms.module.user.entity.Permission;
import com.qms.module.user.entity.User;
import com.qms.module.user.entity.UserPermission;
import com.qms.module.user.repository.PermissionRepository;
import com.qms.module.user.repository.UserPermissionRepository;
import com.qms.module.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserPermissionRepository userPermissionRepository;
    private final PermissionRepository permissionRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsernameOrEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found with username or email: " + username));
        return UserPrincipal.create(user, effectivePermissions(user));
    }

    /**
     * Compute the user's final effective permission set:
     *   (role.permissions for each role) ∪ (user_permissions GRANTs)
     *                                    \ (user_permissions REVOKEs)
     *
     * Resolves user_permissions ids → names in one batch query so the
     * resulting Set carries names Spring Security's hasAuthority()
     * matches against.
     */
    private Set<String> effectivePermissions(User user) {
        Set<String> perms = user.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(Permission::getName)
                .collect(Collectors.toCollection(HashSet::new));

        List<UserPermission> overrides = userPermissionRepository.findByUserId(user.getId());
        if (overrides.isEmpty()) return perms;

        Set<Long> ids = overrides.stream().map(UserPermission::getPermissionId).collect(Collectors.toSet());
        Map<Long, String> idToName = new HashMap<>();
        permissionRepository.findAllById(ids).forEach(p -> idToName.put(p.getId(), p.getName()));

        for (UserPermission up : overrides) {
            String name = idToName.get(up.getPermissionId());
            if (name == null) continue;
            if (up.getGrantType() == UserPermission.GrantType.GRANT)  perms.add(name);
            if (up.getGrantType() == UserPermission.GrantType.REVOKE) perms.remove(name);
        }
        return perms;
    }
}
