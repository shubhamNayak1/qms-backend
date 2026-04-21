package com.qms.module.user.mapper;

import com.qms.module.user.dto.response.PermissionResponse;
import com.qms.module.user.dto.response.RoleResponse;
import com.qms.module.user.dto.response.UserResponse;
import com.qms.module.user.entity.Permission;
import com.qms.module.user.entity.Role;
import com.qms.module.user.entity.User;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-04-20T14:09:00+0530",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 22.0.2 (Homebrew)"
)
@Component
public class UserMapperImpl implements UserMapper {

    @Override
    public PermissionResponse toPermissionResponse(Permission permission) {
        if ( permission == null ) {
            return null;
        }

        PermissionResponse.PermissionResponseBuilder permissionResponse = PermissionResponse.builder();

        permissionResponse.id( permission.getId() );
        permissionResponse.name( permission.getName() );
        permissionResponse.displayName( permission.getDisplayName() );
        permissionResponse.module( permission.getModule() );
        permissionResponse.description( permission.getDescription() );
        permissionResponse.createdAt( permission.getCreatedAt() );
        permissionResponse.createdBy( permission.getCreatedBy() );

        return permissionResponse.build();
    }

    @Override
    public Set<PermissionResponse> toPermissionResponses(Set<Permission> permissions) {
        if ( permissions == null ) {
            return null;
        }

        Set<PermissionResponse> set = new LinkedHashSet<PermissionResponse>( Math.max( (int) ( permissions.size() / .75f ) + 1, 16 ) );
        for ( Permission permission : permissions ) {
            set.add( toPermissionResponse( permission ) );
        }

        return set;
    }

    @Override
    public RoleResponse toRoleResponse(Role role) {
        if ( role == null ) {
            return null;
        }

        RoleResponse.RoleResponseBuilder roleResponse = RoleResponse.builder();

        roleResponse.id( role.getId() );
        roleResponse.name( role.getName() );
        roleResponse.displayName( role.getDisplayName() );
        roleResponse.description( role.getDescription() );
        roleResponse.isSystemRole( role.getIsSystemRole() );
        roleResponse.permissions( toPermissionResponses( role.getPermissions() ) );
        roleResponse.createdAt( role.getCreatedAt() );
        roleResponse.updatedAt( role.getUpdatedAt() );
        roleResponse.createdBy( role.getCreatedBy() );

        return roleResponse.build();
    }

    @Override
    public UserResponse toUserResponse(User user) {
        if ( user == null ) {
            return null;
        }

        UserResponse.UserResponseBuilder userResponse = UserResponse.builder();

        userResponse.id( user.getId() );
        userResponse.username( user.getUsername() );
        userResponse.email( user.getEmail() );
        userResponse.firstName( user.getFirstName() );
        userResponse.lastName( user.getLastName() );
        userResponse.phone( user.getPhone() );
        userResponse.department( user.getDepartment() );
        userResponse.designation( user.getDesignation() );
        userResponse.employeeId( user.getEmployeeId() );
        userResponse.profilePictureUrl( user.getProfilePictureUrl() );
        userResponse.isActive( user.getIsActive() );
        userResponse.isEmailVerified( user.getIsEmailVerified() );
        userResponse.roles( roleSetToRoleResponseSet( user.getRoles() ) );
        userResponse.lastLoginAt( user.getLastLoginAt() );
        userResponse.passwordChangedAt( user.getPasswordChangedAt() );
        userResponse.createdAt( user.getCreatedAt() );
        userResponse.updatedAt( user.getUpdatedAt() );
        userResponse.createdBy( user.getCreatedBy() );

        enrichUserResponse( user, userResponse );

        return userResponse.build();
    }

    protected Set<RoleResponse> roleSetToRoleResponseSet(Set<Role> set) {
        if ( set == null ) {
            return null;
        }

        Set<RoleResponse> set1 = new LinkedHashSet<RoleResponse>( Math.max( (int) ( set.size() / .75f ) + 1, 16 ) );
        for ( Role role : set ) {
            set1.add( toRoleResponse( role ) );
        }

        return set1;
    }
}
