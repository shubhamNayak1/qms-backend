package com.qms.module.user.service;

import com.qms.common.exception.AppException;
import com.qms.module.user.dto.request.AssignRolesRequest;
import com.qms.module.user.dto.request.ChangePasswordRequest;
import com.qms.module.user.dto.request.CreateUserRequest;
import com.qms.module.user.dto.request.UpdateUserRequest;
import com.qms.module.user.dto.response.UserResponse;
import com.qms.module.user.entity.Permission;
import com.qms.module.user.entity.Role;
import com.qms.module.user.entity.User;
import com.qms.module.user.mapper.UserMapper;
import com.qms.module.user.repository.RoleRepository;
import com.qms.module.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService")
class UserServiceTest {

    @Mock private UserRepository  userRepository;
    @Mock private RoleRepository  roleRepository;
    @Mock private UserMapper      userMapper;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    // ─── Test fixtures ────────────────────────────────────────

    private Role employeeRole;
    private User sampleUser;
    private UserResponse sampleResponse;

    @BeforeEach
    void setUp() {
        Permission viewPerm = Permission.builder()
                .name("DOCUMENT_VIEW").displayName("View Documents")
                .module("DMS").build();

        employeeRole = Role.builder()
                .name("EMPLOYEE").displayName("Employee")
                .isSystemRole(false)
                .permissions(Set.of(viewPerm))
                .build();

        sampleUser = User.builder()
                .username("john.doe")
                .email("john.doe@company.com")
                .passwordHash("$2a$12$hashedPassword")
                .firstName("John")
                .lastName("Doe")
                .department("QA")
                .employeeId("EMP-001")
                .isActive(true)
                .isEmailVerified(false)
                .failedLoginAttempts(0)
                .roles(new java.util.HashSet<>(Set.of(employeeRole)))
                .build();

        sampleResponse = UserResponse.builder()
                .id(1L)
                .username("john.doe")
                .email("john.doe@company.com")
                .firstName("John")
                .lastName("Doe")
                .fullName("John Doe")
                .isActive(true)
                .build();
    }

    // ─────────────────────────────────────────────────────────
    @Nested
    @DisplayName("create()")
    class CreateUserTests {

        @Test
        @DisplayName("successfully creates a user with default EMPLOYEE role")
        void create_success_withDefaultRole() {
            CreateUserRequest req = new CreateUserRequest();
            req.setUsername("john.doe");
            req.setEmail("john.doe@company.com");
            req.setPassword("SecurePass@123");
            req.setFirstName("John");
            req.setLastName("Doe");
            req.setEmployeeId("EMP-001");

            when(userRepository.existsByUsernameAndIsDeletedFalse("john.doe")).thenReturn(false);
            when(userRepository.existsByEmailAndIsDeletedFalse("john.doe@company.com")).thenReturn(false);
            when(userRepository.existsByEmployeeIdAndIsDeletedFalse("EMP-001")).thenReturn(false);
            when(roleRepository.findByNameAndIsDeletedFalse("EMPLOYEE")).thenReturn(Optional.of(employeeRole));
            when(passwordEncoder.encode("SecurePass@123")).thenReturn("$2a$12$hashed");
            when(userRepository.save(any(User.class))).thenReturn(sampleUser);
            when(userMapper.toUserResponse(sampleUser)).thenReturn(sampleResponse);

            UserResponse result = userService.create(req);

            assertThat(result).isNotNull();
            assertThat(result.getUsername()).isEqualTo("john.doe");
            verify(passwordEncoder).encode("SecurePass@123");
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("throws CONFLICT when username already exists")
        void create_throwsConflict_whenUsernameExists() {
            CreateUserRequest req = new CreateUserRequest();
            req.setUsername("john.doe");
            req.setEmail("john.doe@company.com");
            req.setPassword("SecurePass@123");
            req.setFirstName("John");
            req.setLastName("Doe");

            when(userRepository.existsByUsernameAndIsDeletedFalse("john.doe")).thenReturn(true);

            assertThatThrownBy(() -> userService.create(req))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("already taken");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws CONFLICT when email already exists")
        void create_throwsConflict_whenEmailExists() {
            CreateUserRequest req = new CreateUserRequest();
            req.setUsername("john.doe");
            req.setEmail("john.doe@company.com");
            req.setPassword("SecurePass@123");
            req.setFirstName("John");
            req.setLastName("Doe");

            when(userRepository.existsByUsernameAndIsDeletedFalse("john.doe")).thenReturn(false);
            when(userRepository.existsByEmailAndIsDeletedFalse("john.doe@company.com")).thenReturn(true);

            assertThatThrownBy(() -> userService.create(req))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("already registered");
        }

        @Test
        @DisplayName("assigns specified roles when roleIds are provided")
        void create_assignsSpecifiedRoles() {
            Role qaRole = Role.builder().name("QA_OFFICER").displayName("QA Officer")
                    .isSystemRole(false).permissions(Set.of()).build();

            CreateUserRequest req = new CreateUserRequest();
            req.setUsername("jane.smith");
            req.setEmail("jane.smith@company.com");
            req.setPassword("SecurePass@123");
            req.setFirstName("Jane");
            req.setLastName("Smith");
            req.setRoleIds(Set.of(2L));

            when(userRepository.existsByUsernameAndIsDeletedFalse("jane.smith")).thenReturn(false);
            when(userRepository.existsByEmailAndIsDeletedFalse("jane.smith@company.com")).thenReturn(false);
            when(roleRepository.findAllByIdInAndIsDeletedFalse(Set.of(2L))).thenReturn(Set.of(qaRole));
            when(passwordEncoder.encode(any())).thenReturn("$2a$12$hashed");
            when(userRepository.save(any(User.class))).thenReturn(sampleUser);
            when(userMapper.toUserResponse(sampleUser)).thenReturn(sampleResponse);

            userService.create(req);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getRoles()).contains(qaRole);
        }
    }

    // ─────────────────────────────────────────────────────────
    @Nested
    @DisplayName("update()")
    class UpdateUserTests {

        @Test
        @DisplayName("updates only non-null fields")
        void update_patchBehavior_onlyNonNullFields() {
            UpdateUserRequest req = new UpdateUserRequest();
            req.setFirstName("Jonathan");
            // lastName, phone etc. are null → should not be changed

            when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(sampleUser));
            when(userRepository.save(any())).thenReturn(sampleUser);
            when(userMapper.toUserResponse(sampleUser)).thenReturn(sampleResponse);

            userService.update(1L, req);

            assertThat(sampleUser.getFirstName()).isEqualTo("Jonathan");
            assertThat(sampleUser.getLastName()).isEqualTo("Doe");   // unchanged
        }

        @Test
        @DisplayName("throws NOT_FOUND for non-existent user")
        void update_throwsNotFound() {
            when(userRepository.findByIdAndIsDeletedFalse(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.update(99L, new UpdateUserRequest()))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("not found");
        }
    }

    // ─────────────────────────────────────────────────────────
    @Nested
    @DisplayName("changePassword()")
    class ChangePasswordTests {

        @Test
        @DisplayName("successfully changes password when current password is correct")
        void changePassword_success() {
            ChangePasswordRequest req = new ChangePasswordRequest();
            req.setCurrentPassword("OldPass@123");
            req.setNewPassword("NewPass@456");
            req.setConfirmPassword("NewPass@456");

            when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(sampleUser));
            when(passwordEncoder.matches("OldPass@123", sampleUser.getPasswordHash())).thenReturn(true);
            when(passwordEncoder.matches("NewPass@456", sampleUser.getPasswordHash())).thenReturn(false);
            when(passwordEncoder.encode("NewPass@456")).thenReturn("$2a$12$newHash");

            userService.changePassword(1L, req);

            verify(userRepository).updatePassword(eq(1L), eq("$2a$12$newHash"), any());
        }

        @Test
        @DisplayName("throws BAD_REQUEST when current password is wrong")
        void changePassword_wrongCurrent() {
            ChangePasswordRequest req = new ChangePasswordRequest();
            req.setCurrentPassword("WrongPass@123");
            req.setNewPassword("NewPass@456");
            req.setConfirmPassword("NewPass@456");

            when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(sampleUser));
            when(passwordEncoder.matches("WrongPass@123", sampleUser.getPasswordHash())).thenReturn(false);

            assertThatThrownBy(() -> userService.changePassword(1L, req))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("Current password is incorrect");

            verify(userRepository, never()).updatePassword(any(), any(), any());
        }

        @Test
        @DisplayName("throws BAD_REQUEST when confirm password does not match")
        void changePassword_mismatch() {
            ChangePasswordRequest req = new ChangePasswordRequest();
            req.setCurrentPassword("OldPass@123");
            req.setNewPassword("NewPass@456");
            req.setConfirmPassword("DifferentPass@789");

            when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(sampleUser));
            when(passwordEncoder.matches("OldPass@123", sampleUser.getPasswordHash())).thenReturn(true);

            assertThatThrownBy(() -> userService.changePassword(1L, req))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("do not match");
        }

        @Test
        @DisplayName("throws BAD_REQUEST when new password equals old password")
        void changePassword_sameAsOld() {
            ChangePasswordRequest req = new ChangePasswordRequest();
            req.setCurrentPassword("SamePass@123");
            req.setNewPassword("SamePass@123");
            req.setConfirmPassword("SamePass@123");

            when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(sampleUser));
            when(passwordEncoder.matches("SamePass@123", sampleUser.getPasswordHash())).thenReturn(true);

            assertThatThrownBy(() -> userService.changePassword(1L, req))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("different from the current");
        }
    }

    // ─────────────────────────────────────────────────────────
    @Nested
    @DisplayName("assignRoles()")
    class AssignRolesTests {

        @Test
        @DisplayName("replaces role set atomically")
        void assignRoles_replacesEntireSet() {
            Role qaRole = Role.builder().name("QA_OFFICER").displayName("QA Officer")
                    .isSystemRole(false).permissions(Set.of()).users(new java.util.HashSet<>()).build();

            AssignRolesRequest req = new AssignRolesRequest();
            req.setRoleIds(Set.of(2L));

            when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(sampleUser));
            when(roleRepository.findAllByIdInAndIsDeletedFalse(Set.of(2L))).thenReturn(Set.of(qaRole));
            when(userRepository.save(any())).thenReturn(sampleUser);
            when(userMapper.toUserResponse(sampleUser)).thenReturn(sampleResponse);

            userService.assignRoles(1L, req);

            // employeeRole should have been removed; qaRole added
            verify(userRepository).save(argThat(u ->
                    u.getRoles().contains(qaRole) && !u.getRoles().contains(employeeRole)));
        }

        @Test
        @DisplayName("throws BAD_REQUEST when a role ID does not exist")
        void assignRoles_invalidRoleId() {
            AssignRolesRequest req = new AssignRolesRequest();
            req.setRoleIds(Set.of(99L));

            when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(sampleUser));
            when(roleRepository.findAllByIdInAndIsDeletedFalse(Set.of(99L))).thenReturn(Set.of());

            assertThatThrownBy(() -> userService.assignRoles(1L, req))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("invalid");
        }
    }

    // ─────────────────────────────────────────────────────────
    @Nested
    @DisplayName("softDelete()")
    class SoftDeleteTests {

        @Test
        @DisplayName("sets isDeleted=true and isActive=false")
        void softDelete_setsFlags() {
            when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(sampleUser));
            when(userRepository.save(any())).thenReturn(sampleUser);

            userService.softDelete(1L);

            assertThat(sampleUser.getIsDeleted()).isTrue();
            assertThat(sampleUser.getIsActive()).isFalse();
            verify(userRepository).save(sampleUser);
        }

        @Test
        @DisplayName("throws NOT_FOUND for already-deleted user")
        void softDelete_throwsNotFound_whenAlreadyDeleted() {
            when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.softDelete(1L))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("not found");
        }
    }
}
