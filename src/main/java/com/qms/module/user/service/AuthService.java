package com.qms.module.user.service;

import com.qms.common.enums.AuditAction;
import com.qms.common.enums.AuditModule;
import com.qms.common.exception.AppException;
import com.qms.module.audit.annotation.Audited;
import com.qms.module.user.dto.request.LoginRequest;
import com.qms.module.user.dto.request.RefreshTokenRequest;
import com.qms.module.user.dto.response.TokenResponse;
import com.qms.module.user.entity.User;
import com.qms.module.user.repository.UserRepository;
import com.qms.module.user.service.PasswordPolicyService;
import com.qms.security.JwtTokenProvider;
import com.qms.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider      jwtTokenProvider;
    private final UserRepository        userRepository;
    private final PasswordEncoder       passwordEncoder;
    private final PasswordPolicyService passwordPolicyService;

    @Value("${app.security.max-failed-attempts:5}")
    private int maxFailedAttempts;

    @Value("${app.security.lock-duration-minutes:30}")
    private int lockDurationMinutes;

    // ─── Login ───────────────────────────────────────────────

    @Audited(action = AuditAction.LOGIN, module = AuditModule.AUTH,
             entityType = "User", captureNewValue = false,
             description = "User login")
    @Transactional
    public TokenResponse login(LoginRequest req) {
        // Pre-auth: check if account is locked before attempting Spring Security auth
        userRepository.findByUsernameOrEmail(req.getUsernameOrEmail())
                .ifPresent(this::checkAccountLocked);

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            req.getUsernameOrEmail(),
                            req.getPassword()
                    )
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

            // Reset failed attempts on successful login
            userRepository.resetLockout(principal.getId());
            userRepository.updateLastLoginAt(principal.getId(), LocalDateTime.now());

            // Issue tokens
            String accessToken  = jwtTokenProvider.generateAccessToken(principal);
            String refreshToken = jwtTokenProvider.generateRefreshToken(principal);

            // Store BCrypt hash of refresh token for secure rotation validation
            userRepository.updateRefreshTokenHash(
                    principal.getId(),
                    passwordEncoder.encode(refreshToken));

            log.info("Login successful: user='{}' (id={})", principal.getUsername(), principal.getId());
            return buildTokenResponse(principal, accessToken, refreshToken);

        } catch (BadCredentialsException ex) {
            // Increment failed attempts and potentially lock the account
            handleFailedLogin(req.getUsernameOrEmail());
            throw ex; // re-throw for GlobalExceptionHandler to produce correct 401
        }
    }

    // ─── Refresh ─────────────────────────────────────────────

    @Audited(action = AuditAction.TOKEN_REFRESHED, module = AuditModule.AUTH,
             entityType = "User", captureNewValue = false,
             description = "Token refresh")
    @Transactional
    public TokenResponse refresh(RefreshTokenRequest req) {
        String token = req.getRefreshToken();

        if (!jwtTokenProvider.validateToken(token)) {
            throw AppException.tokenExpired();
        }

        String username = jwtTokenProvider.getUsernameFromToken(token);
        Long   userId   = jwtTokenProvider.getUserIdFromToken(token);

        User user = userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> AppException.unauthorized("User not found for refresh token"));

        if (!user.getIsActive()) {
            throw AppException.accountDisabled();
        }

        // Validate the refresh token against the stored hash (rotation check)
        if (user.getRefreshTokenHash() == null
                || !passwordEncoder.matches(token, user.getRefreshTokenHash())) {
            // Token has already been rotated — potential reuse attack
            log.warn("Refresh token reuse detected for user '{}'", username);
            userRepository.updateRefreshTokenHash(userId, null); // invalidate all sessions
            throw AppException.invalidToken();
        }

        UserPrincipal principal = UserPrincipal.create(user);

        // Rotate: issue new token pair
        String newAccessToken  = jwtTokenProvider.generateAccessToken(principal);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(principal);

        userRepository.updateRefreshTokenHash(userId, passwordEncoder.encode(newRefreshToken));

        log.debug("Tokens rotated for user '{}'", username);
        return buildTokenResponse(principal, newAccessToken, newRefreshToken);
    }

    // ─── Logout ──────────────────────────────────────────────

    @Audited(action = AuditAction.LOGOUT, module = AuditModule.AUTH,
             entityType = "User", captureNewValue = false,
             description = "User logout")
    @Transactional
    public void logout() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            // Invalidate refresh token — access token is short-lived and expires naturally
            userRepository.updateRefreshTokenHash(principal.getId(), null);
            log.info("User '{}' logged out", principal.getUsername());
        }
        SecurityContextHolder.clearContext();
    }

    // ─── Helpers ─────────────────────────────────────────────

    private void checkAccountLocked(User user) {
        if (user.isAccountLocked()) {
            log.warn("Login attempt on locked account: '{}'", user.getUsername());
            throw new LockedException("Account is temporarily locked");
        }
    }

    @Transactional
    protected void handleFailedLogin(String usernameOrEmail) {
        userRepository.findByUsernameOrEmail(usernameOrEmail).ifPresent(user -> {
            int attempts = user.getFailedLoginAttempts() + 1;
            // Set the incremented value on the entity before save — avoids the stale-entity
            // overwrite that would occur if we called updateFailedAttempts (JPQL) then save().
            user.setFailedLoginAttempts(attempts);

            if (attempts >= maxFailedAttempts) {
                LocalDateTime lockUntil = LocalDateTime.now().plusMinutes(lockDurationMinutes);
                user.setLockedUntil(lockUntil);
                log.warn("Account '{}' locked until {} after {} failed attempts",
                        user.getUsername(), lockUntil, attempts);
            }
            userRepository.save(user);
        });
    }

    private TokenResponse buildTokenResponse(UserPrincipal principal,
                                              String accessToken,
                                              String refreshToken) {
        // mustChangePassword is true if the flag is set OR if the password has expired
        boolean mustChange = principal.isMustChangePassword()
                || isPasswordExpired(principal.getId());

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .accessExpiresIn(jwtTokenProvider.getAccessExpirySeconds())
                .refreshExpiresIn(jwtTokenProvider.getRefreshExpirySeconds())
                .userId(principal.getId())
                .username(principal.getUsername())
                .email(principal.getEmail())
                .fullName(principal.getFullName())
                .roles(principal.getRoleNames())
                .permissions(principal.getPermissionNames())
                .mustChangePassword(mustChange)
                .build();
    }

    /**
     * Returns true if the user's password has exceeded the active policy's validPeriod.
     * Always false when validPeriod = 0 (passwords never expire).
     */
    private boolean isPasswordExpired(Long userId) {
        int validPeriod = passwordPolicyService.getActiveValidPeriod();
        if (validPeriod <= 0) return false;

        return userRepository.findByIdAndIsDeletedFalse(userId)
                .map(user -> {
                    if (user.getPasswordChangedAt() == null) return true; // never changed → treat as expired
                    return user.getPasswordChangedAt()
                            .plusDays(validPeriod)
                            .isBefore(LocalDateTime.now());
                })
                .orElse(false);
    }
}
