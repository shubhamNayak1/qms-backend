package com.qms.module.user.mapper;

import com.qms.module.user.dto.response.PermissionResponse;
import com.qms.module.user.dto.response.RoleResponse;
import com.qms.module.user.dto.response.UserResponse;
import com.qms.module.user.entity.Permission;
import com.qms.module.user.entity.Role;
import com.qms.module.user.entity.User;
import org.mapstruct.*;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * MapStruct mapper — translates JPA entities to response DTOs.
 *
 * Notes:
 *  - componentModel = "spring" → registered as a Spring bean; inject with @Autowired / constructor
 *  - unmappedTargetPolicy = IGNORE → no compile error for fields not in the entity (e.g. userCount)
 *  - fullName and permissions are computed via @AfterMapping
 */
@Mapper(
    componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface UserMapper {

    // ─── Permission ─────────────────────────────────────────

    @Mapping(target = "id",          source = "id")
    @Mapping(target = "name",        source = "name")
    @Mapping(target = "displayName", source = "displayName")
    @Mapping(target = "module",      source = "module")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "createdAt",   source = "createdAt")
    @Mapping(target = "createdBy",   source = "createdBy")
    PermissionResponse toPermissionResponse(Permission permission);

    Set<PermissionResponse> toPermissionResponses(Set<Permission> permissions);

    // ─── Role ───────────────────────────────────────────────

    @Mapping(target = "id",          source = "id")
    @Mapping(target = "name",        source = "name")
    @Mapping(target = "displayName", source = "displayName")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "isSystemRole",source = "isSystemRole")
    @Mapping(target = "permissions", source = "permissions")
    @Mapping(target = "createdAt",   source = "createdAt")
    @Mapping(target = "updatedAt",   source = "updatedAt")
    @Mapping(target = "createdBy",   source = "createdBy")
    @Mapping(target = "userCount",   ignore = true)  // populated by service
    RoleResponse toRoleResponse(Role role);

    // ─── User ───────────────────────────────────────────────

    @Mapping(target = "id",                source = "id")
    @Mapping(target = "username",          source = "username")
    @Mapping(target = "email",             source = "email")
    @Mapping(target = "firstName",         source = "firstName")
    @Mapping(target = "lastName",          source = "lastName")
    @Mapping(target = "phone",             source = "phone")
    @Mapping(target = "department",        source = "department")
    @Mapping(target = "designation",       source = "designation")
    @Mapping(target = "employeeId",        source = "employeeId")
    @Mapping(target = "profilePictureUrl", source = "profilePictureUrl")
    @Mapping(target = "isActive",          source = "isActive")
    @Mapping(target = "isEmailVerified",   source = "isEmailVerified")
    @Mapping(target = "roles",             source = "roles")
    @Mapping(target = "lastLoginAt",       source = "lastLoginAt")
    @Mapping(target = "passwordChangedAt", source = "passwordChangedAt")
    @Mapping(target = "createdAt",         source = "createdAt")
    @Mapping(target = "updatedAt",         source = "updatedAt")
    @Mapping(target = "createdBy",         source = "createdBy")
    @Mapping(target = "fullName",    ignore = true)  // set in @AfterMapping
    @Mapping(target = "permissions", ignore = true)  // set in @AfterMapping
    UserResponse toUserResponse(User user);

    @AfterMapping
    default void enrichUserResponse(User user, @MappingTarget UserResponse.UserResponseBuilder resp) {
        // Computed full name
        resp.fullName(user.getFullName());

        // Flat set of all permission names across all roles
        Set<String> perms = user.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(Permission::getName)
                .collect(Collectors.toSet());
        resp.permissions(perms);
    }
}
