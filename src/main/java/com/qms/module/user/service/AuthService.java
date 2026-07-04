package com.qms.module.user.service;

import com.qms.common.enums.AuditAction;
import com.qms.common.enums.AuditModule;
import com.qms.common.exception.AppException;
import com.qms.module.audit.annotation.Audited;
import com.qms.module.audit.context.AuditContext;
import com.qms.module.audit.context.AuditContextHolder;
import com.qms.module.license.service.LicenseService;
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

    private static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider      jwtTokenProvider;
    private final UserRepository        userRepository;
    private final PasswordEncoder       passwordEncoder;
    private final PasswordPolicyService passwordPolicyService;
    private final LicenseService        licenseService;

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

            // ── License gate ─────────────────────────────────
            // SUPER_ADMIN bypasses the license check so the very first admin
            // can always log in to assign licenses to other users.
            // All other users MUST hold an active assigned license.
            boolean isSuperAdmin = principal.getAuthorities().stream()
                    .anyMatch(a -> ("ROLE_" + SUPER_ADMIN_ROLE).equals(a.getAuthority()));
            if (!isSuperAdmin && !licenseService.userHasActiveLicense(principal.getId())) {
                log.warn("Login blocked: user '{}' (id={}) has no active license",
                        principal.getUsername(), principal.getId());
                // Clear the partial security context so a downstream check
                // doesn't see this half-authenticated principal.
                SecurityContextHolder.clearContext();
                throw new AppException(
                        org.springframework.http.HttpStatus.FORBIDDEN,
                        "LICENSE_REQUIRED",
                        "No active license is assigned to your account. Please contact an administrator.");
            }

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
            // Round-M (2026-06-27) tester CC-Point-1 · Issue 1: publish
            // a friendly description for the failed-login audit row so
            // the User Activity Trail records who tried to log in with
            // what username and why it failed. The @Audited(logOnFailure)
            // aspect will pick this context up when it builds the FAILURE
            // row after the exception is re-thrown.
            publishFailedLoginContext(req.getUsernameOrEmail(),
                    "BAD_CREDENTIALS", "wrong password");
            // Increment failed attempts and potentially lock the account
            handleFailedLogin(req.getUsernameOrEmail());
            throw ex; // re-throw for GlobalExceptionHandler to produce correct 401
        } catch (LockedException ex) {
            publishFailedLoginContext(req.getUsernameOrEmail(),
                    "ACCOUNT_LOCKED", "account is temporarily locked");
            throw ex;
        } catch (AppException ex) {
            // Covers the LICENSE_REQUIRED / other business-rule failures
            // thrown between successful auth and token issuance so the
            // audit trail explains why login was blocked.
            publishFailedLoginContext(req.getUsernameOrEmail(),
                    ex.getErrorCode() != null ? ex.getErrorCode() : "LOGIN_BLOCKED",
                    ex.getMessage());
            throw ex;
        }
    }

    /**
     * Round-M (2026-06-27) tester CC-Point-1 · Issue 1: writes a
     * description + attempted-username payload into the thread-local
     * audit context so the FAILURE audit row surfaces the *what* and
     * *why*. Called from every catch branch of {@link #login}.
     */
    private void publishFailedLoginContext(String attemptedUsername,
                                            String reason,
                                            String detail) {
        // If the username matches a real user, populate entityId so the
        // Audit Trail's user-filter can find the row. When the username
        // does not exist we still write the FAILURE row with entityId=null
        // — the description carries the attempted string.
        Long userId = userRepository.findByUsernameOrEmail(attemptedUsername)
                .map(User::getId)
                .orElse(null);
        String desc = String.format(
                "Failed login attempt: username='%s' · reason=%s%s",
                attemptedUsername,
                reason,
                (detail != null && !detail.isBlank()) ? " · " + detail : "");
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("attemptedUsername", attemptedUsername);
        payload.put("reason", reason);
        if (detail != null && !detail.isBlank()) payload.put("detail", detail);
        AuditContextHolder.set(AuditContext.builder()
                .entityType("User")
                .entityId(userId)
                .description(desc.length() > 480 ? desc.substring(0, 479) + "…" : desc)
                .additionalData(payload)
                .build());
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

    // ─── 21 CFR Part 11 e-signature gate (Round-2 E3) ────────
    //
    // Workflow transitions (Submit, Approve, Reject, Resend, Close, Cancel)
    // require the current user to re-confirm their password. We do NOT issue
    // a new JWT — the caller's existing token continues to authorise the
    // subsequent workflow API call. The verification is logged via the
    // @Audited annotation so the audit_log carries who, when, and what was
    // being signed.
    //
    // We keep the surface minimal: client posts { meaning } so the audit
    // entry can record what the signature was for, and a Basic-style
    // `username + password` body. The caller MUST already be authenticated;
    // the username on the request must match the authenticated principal.

    @Audited(action = AuditAction.LOGIN, module = AuditModule.AUTH,
             entityType = "User", captureNewValue = false,
             description = "E-signature verification")
    public boolean verifyESignature(String username, String password, String meaning) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() ||
                !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            throw AppException.unauthorized("Not authenticated");
        }
        if (!principal.getUsername().equalsIgnoreCase(username)) {
            // Spoofing a different username is a hard fail.
            log.warn("E-sign attempted with mismatched username: principal='{}' submitted='{}'",
                    principal.getUsername(), username);
            throw AppException.forbidden(
                    "E-signature username must match the logged-in user.");
        }
        User user = userRepository.findByIdAndIsDeletedFalse(principal.getId())
                .orElseThrow(() -> AppException.unauthorized("User not found"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            log.warn("E-sign failed: bad password for user '{}' (meaning='{}')",
                    username, meaning);
            // Don't increment lock counters — e-sign re-auth is not a fresh
            // login attempt. A bad password is just denied and the caller
            // can retry. Repeated abuse is caught by general rate limits.
            throw AppException.unauthorized("Incorrect password.");
        }
        log.info("E-sign OK: user='{}' meaning='{}'", username, meaning);
        return true;
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
