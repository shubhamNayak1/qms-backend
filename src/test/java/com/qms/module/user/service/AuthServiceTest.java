package com.qms.module.user.service;

import com.qms.common.exception.AppException;
import com.qms.module.user.dto.request.LoginRequest;
import com.qms.module.user.dto.request.RefreshTokenRequest;
import com.qms.module.user.dto.response.TokenResponse;
import com.qms.module.user.entity.Permission;
import com.qms.module.user.entity.Role;
import com.qms.module.user.entity.User;
import com.qms.module.user.repository.UserRepository;
import com.qms.security.JwtTokenProvider;
import com.qms.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtTokenProvider      jwtTokenProvider;
    @Mock private UserRepository        userRepository;
    @Mock private PasswordEncoder       passwordEncoder;

    @InjectMocks
    private AuthService authService;

    private User activeUser;
    private UserPrincipal userPrincipal;

    @BeforeEach
    void setUp() {
        // Inject @Value fields
        ReflectionTestUtils.setField(authService, "maxFailedAttempts", 5);
        ReflectionTestUtils.setField(authService, "lockDurationMinutes", 30);

        Role employeeRole = Role.builder().name("EMPLOYEE").displayName("Employee")
                .isSystemRole(false).permissions(Set.of()).build();

        activeUser = User.builder().username("john.doe").email("john.doe@company.com")
                .passwordHash("$2a$12$hashed").firstName("John").lastName("Doe")
                .isActive(true).isEmailVerified(true).failedLoginAttempts(0)
                .roles(new java.util.HashSet<>(Set.of(employeeRole)))
                .build();

        userPrincipal = UserPrincipal.create(activeUser);
    }

    // ─────────────────────────────────────────────────────────
    @Nested
    @DisplayName("login()")
    class LoginTests {

        @Test
        @DisplayName("returns token response on valid credentials")
        void login_success() {
            LoginRequest req = new LoginRequest();
            req.setUsernameOrEmail("john.doe");
            req.setPassword("SecurePass@123");

            Authentication auth = mock(Authentication.class);
            when(auth.getPrincipal()).thenReturn(userPrincipal);
            when(userRepository.findByUsernameOrEmail("john.doe")).thenReturn(Optional.of(activeUser));
            when(authenticationManager.authenticate(any())).thenReturn(auth);
            when(jwtTokenProvider.generateAccessToken(userPrincipal)).thenReturn("access.token.here");
            when(jwtTokenProvider.generateRefreshToken(userPrincipal)).thenReturn("refresh.token.here");
            when(jwtTokenProvider.getAccessExpirySeconds()).thenReturn(900L);
            when(jwtTokenProvider.getRefreshExpirySeconds()).thenReturn(604800L);
            when(passwordEncoder.encode("refresh.token.here")).thenReturn("$2a$12$refreshHash");

            TokenResponse result = authService.login(req);

            assertThat(result.getAccessToken()).isEqualTo("access.token.here");
            assertThat(result.getRefreshToken()).isEqualTo("refresh.token.here");
            assertThat(result.getTokenType()).isEqualTo("Bearer");
            assertThat(result.getUserId()).isEqualTo(1L);
            verify(userRepository).resetLockout(1L);
            verify(userRepository).updateLastLoginAt(eq(1L), any());
            verify(userRepository).updateRefreshTokenHash(1L, "$2a$12$refreshHash");
        }

        @Test
        @DisplayName("increments failedLoginAttempts on bad credentials")
        void login_incrementsAttempts_onBadCredentials() {
            LoginRequest req = new LoginRequest();
            req.setUsernameOrEmail("john.doe");
            req.setPassword("WrongPassword");

            when(userRepository.findByUsernameOrEmail("john.doe")).thenReturn(Optional.of(activeUser));
            when(authenticationManager.authenticate(any()))
                    .thenThrow(new BadCredentialsException("Bad credentials"));

            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(BadCredentialsException.class);

            verify(userRepository).updateFailedAttempts(eq(1L), eq(1));
        }

        @Test
        @DisplayName("locks account after max failed attempts")
        void login_locksAccount_afterMaxAttempts() {
            activeUser.setFailedLoginAttempts(4); // one more will trigger lock

            LoginRequest req = new LoginRequest();
            req.setUsernameOrEmail("john.doe");
            req.setPassword("WrongPassword");

            when(userRepository.findByUsernameOrEmail("john.doe")).thenReturn(Optional.of(activeUser));
            when(authenticationManager.authenticate(any()))
                    .thenThrow(new BadCredentialsException("Bad credentials"));

            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(BadCredentialsException.class);

            verify(userRepository).save(argThat(u -> u.getLockedUntil() != null));
        }
    }

    // ─────────────────────────────────────────────────────────
    @Nested
    @DisplayName("refresh()")
    class RefreshTests {

        @Test
        @DisplayName("issues new token pair on valid refresh token")
        void refresh_success() {
            RefreshTokenRequest req = new RefreshTokenRequest();
            req.setRefreshToken("valid.refresh.token");

            activeUser.setRefreshTokenHash("$2a$12$storedRefreshHash");

            when(jwtTokenProvider.validateToken("valid.refresh.token")).thenReturn(true);
            when(jwtTokenProvider.getUsernameFromToken("valid.refresh.token")).thenReturn("john.doe");
            when(jwtTokenProvider.getUserIdFromToken("valid.refresh.token")).thenReturn(1L);
            when(jwtTokenProvider.getAccessExpirySeconds()).thenReturn(900L);
            when(jwtTokenProvider.getRefreshExpirySeconds()).thenReturn(604800L);
            when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches("valid.refresh.token", "$2a$12$storedRefreshHash"))
                    .thenReturn(true);
            when(jwtTokenProvider.generateAccessToken(any(UserPrincipal.class)))
                    .thenReturn("new.access.token");
            when(jwtTokenProvider.generateRefreshToken(any(UserPrincipal.class)))
                    .thenReturn("new.refresh.token");
            when(passwordEncoder.encode("new.refresh.token")).thenReturn("$2a$12$newRefreshHash");

            TokenResponse result = authService.refresh(req);

            assertThat(result.getAccessToken()).isEqualTo("new.access.token");
            assertThat(result.getRefreshToken()).isEqualTo("new.refresh.token");
            verify(userRepository).updateRefreshTokenHash(1L, "$2a$12$newRefreshHash");
        }

        @Test
        @DisplayName("throws TOKEN_EXPIRED on invalid refresh token")
        void refresh_throwsTokenExpired_whenInvalidToken() {
            RefreshTokenRequest req = new RefreshTokenRequest();
            req.setRefreshToken("bad.token");

            when(jwtTokenProvider.validateToken("bad.token")).thenReturn(false);

            assertThatThrownBy(() -> authService.refresh(req))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("expired");
        }

        @Test
        @DisplayName("detects refresh token reuse and invalidates all sessions")
        void refresh_detectsTokenReuse() {
            RefreshTokenRequest req = new RefreshTokenRequest();
            req.setRefreshToken("reused.refresh.token");

            activeUser.setRefreshTokenHash("$2a$12$differentHash"); // hash of a different token

            when(jwtTokenProvider.validateToken("reused.refresh.token")).thenReturn(true);
            when(jwtTokenProvider.getUsernameFromToken("reused.refresh.token")).thenReturn("john.doe");
            when(jwtTokenProvider.getUserIdFromToken("reused.refresh.token")).thenReturn(1L);
            when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches("reused.refresh.token", "$2a$12$differentHash"))
                    .thenReturn(false); // does NOT match → reuse detected

            assertThatThrownBy(() -> authService.refresh(req))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("invalid");

            // All sessions invalidated
            verify(userRepository).updateRefreshTokenHash(1L, null);
        }
    }

    // ─────────────────────────────────────────────────────────
    @Nested
    @DisplayName("logout()")
    class LogoutTests {

        @Test
        @DisplayName("clears refresh token hash on logout")
        void logout_clearsRefreshToken() {
            // Simulate authenticated context
            var auth = new UsernamePasswordAuthenticationToken(
                    userPrincipal, null, userPrincipal.getAuthorities());
            org.springframework.security.core.context.SecurityContextHolder
                    .getContext().setAuthentication(auth);

            authService.logout();

            verify(userRepository).updateRefreshTokenHash(1L, null);
        }
    }
}
