package com.qms.module.license.service;

import com.qms.common.enums.AuditAction;
import com.qms.common.enums.AuditModule;
import com.qms.common.exception.AppException;
import com.qms.common.response.PageResponse;
import com.qms.module.audit.annotation.Audited;
import com.qms.module.license.dto.request.GenerateLicensesRequest;
import com.qms.module.license.dto.response.LicenseResponse;
import com.qms.module.license.dto.response.LicenseStatsResponse;
import com.qms.module.license.entity.License;
import com.qms.module.license.enums.LicenseStatus;
import com.qms.module.license.repository.LicenseRepository;
import com.qms.module.user.entity.User;
import com.qms.module.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages the per-seat license pool that gates QMS login.
 *
 * Lifecycle:
 *   1. Admin generates N licenses → AVAILABLE
 *   2. Admin assigns one to a user → ASSIGNED, user can now log in
 *   3. Admin revokes (or expiry passes) → REVOKED / EXPIRED, user blocked
 *
 * License code format: QMS-XXXX-XXXX-XXXX (random uppercase hex).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LicenseService {

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();
    private final SecureRandom random = new SecureRandom();

    private final LicenseRepository licenseRepository;
    private final UserRepository    userRepository;

    // ─────────────────────────────────────────────────────────
    //  Login gate — used by AuthService
    // ─────────────────────────────────────────────────────────

    /**
     * Returns true when the user holds an active assigned, unexpired license.
     * Auto-flips an expired ASSIGNED license to EXPIRED on the way through
     * (lazy expiry — avoids needing a scheduled job).
     */
    @Transactional
    public boolean userHasActiveLicense(Long userId) {
        if (userId == null) return false;
        return licenseRepository.findActiveLicenseForUser(userId, LocalDateTime.now())
                .map(l -> {
                    // Lazy-expire if needed (defensive — query already filters this)
                    if (l.getExpiresAt() != null && l.getExpiresAt().isBefore(LocalDateTime.now())) {
                        l.setStatus(LicenseStatus.EXPIRED);
                        licenseRepository.save(l);
                        return false;
                    }
                    return true;
                })
                .orElse(false);
    }

    // ─────────────────────────────────────────────────────────
    //  Admin operations
    // ─────────────────────────────────────────────────────────

    @Audited(action = AuditAction.CREATE, module = AuditModule.LICENSE,
             entityType = "License",
             description = "License batch generated into AVAILABLE pool")
    @Transactional
    public List<LicenseResponse> generate(GenerateLicensesRequest req) {
        List<LicenseResponse> result = new ArrayList<>();
        for (int i = 0; i < req.getCount(); i++) {
            String code;
            // Defensive collision guard — vanishingly unlikely but cheap.
            do {
                code = newCode();
            } while (licenseRepository.existsByCodeAndIsDeletedFalse(code));

            License lic = License.builder()
                    .code(code)
                    .status(LicenseStatus.AVAILABLE)
                    .expiresAt(req.getExpiresAt())
                    .notes(req.getNotes())
                    .build();
            License saved = licenseRepository.save(lic);
            result.add(toResponse(saved));
        }
        log.info("Generated {} licenses (expires={}, notes={})",
                req.getCount(), req.getExpiresAt(), req.getNotes());
        return result;
    }

    @Audited(action = AuditAction.UPDATE, module = AuditModule.LICENSE,
             entityType = "License", entityIdArgIndex = 0,
             description = "License assigned to user (login enabled)")
    @Transactional
    public LicenseResponse assign(Long licenseId, Long userId) {
        License lic = licenseRepository.findByIdAndIsDeletedFalse(licenseId)
                .orElseThrow(() -> AppException.notFound("License", licenseId));
        if (lic.getStatus() != LicenseStatus.AVAILABLE) {
            throw AppException.badRequest(
                    "License " + lic.getCode() + " is " + lic.getStatus() +
                    " — only AVAILABLE licenses can be assigned.");
        }

        User user = userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> AppException.notFound("User", userId));

        // Block double-assignment — user already holds an active license.
        if (userHasActiveLicense(userId)) {
            throw AppException.conflict(
                    "User '" + user.getUsername() + "' already has an active license.");
        }

        Long actorId = currentUserIdOrNull();
        lic.setStatus(LicenseStatus.ASSIGNED);
        lic.setAssignedToUserId(userId);
        lic.setAssignedAt(LocalDateTime.now());
        lic.setAssignedByUserId(actorId);
        License saved = licenseRepository.save(lic);

        log.info("License {} assigned to user '{}' (id={}) by actor id={}",
                lic.getCode(), user.getUsername(), userId, actorId);
        return toResponse(saved);
    }

    @Audited(action = AuditAction.UPDATE, module = AuditModule.LICENSE,
             entityType = "License", entityIdArgIndex = 0,
             description = "License revoked (user can no longer log in)")
    @Transactional
    public LicenseResponse revoke(Long licenseId, String reason) {
        License lic = licenseRepository.findByIdAndIsDeletedFalse(licenseId)
                .orElseThrow(() -> AppException.notFound("License", licenseId));
        if (lic.getStatus() != LicenseStatus.ASSIGNED) {
            throw AppException.badRequest(
                    "Only ASSIGNED licenses can be revoked. Current status: " + lic.getStatus());
        }
        Long actorId = currentUserIdOrNull();
        lic.setStatus(LicenseStatus.REVOKED);
        lic.setRevokedAt(LocalDateTime.now());
        lic.setRevokedByUserId(actorId);
        if (reason != null && !reason.isBlank()) {
            String existing = lic.getNotes() == null ? "" : lic.getNotes() + " | ";
            lic.setNotes(existing + "REVOKED: " + reason);
        }
        License saved = licenseRepository.save(lic);
        log.info("License {} revoked from user id={} by actor id={}",
                lic.getCode(), lic.getAssignedToUserId(), actorId);
        return toResponse(saved);
    }

    public PageResponse<LicenseResponse> list(LicenseStatus status, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        var pageResult = (status != null
                ? licenseRepository.findAllByStatusAndIsDeletedFalse(status, pageable)
                : licenseRepository.findAllByIsDeletedFalse(pageable));
        return PageResponse.of(pageResult.map(this::toResponse));
    }

    public LicenseStatsResponse stats() {
        long avail   = licenseRepository.countByStatusAndIsDeletedFalse(LicenseStatus.AVAILABLE);
        long assign  = licenseRepository.countByStatusAndIsDeletedFalse(LicenseStatus.ASSIGNED);
        long revoke  = licenseRepository.countByStatusAndIsDeletedFalse(LicenseStatus.REVOKED);
        long expired = licenseRepository.countByStatusAndIsDeletedFalse(LicenseStatus.EXPIRED);
        return LicenseStatsResponse.builder()
                .available(avail).assigned(assign).revoked(revoke).expired(expired)
                .total(avail + assign + revoke + expired)
                .build();
    }

    // ─────────────────────────────────────────────────────────
    //  Internals
    // ─────────────────────────────────────────────────────────

    /** QMS-XXXX-XXXX-XXXX where each X is a hex digit. */
    private String newCode() {
        StringBuilder sb = new StringBuilder("QMS-");
        for (int seg = 0; seg < 3; seg++) {
            for (int i = 0; i < 4; i++) sb.append(HEX[random.nextInt(16)]);
            if (seg < 2) sb.append('-');
        }
        return sb.toString();
    }

    private LicenseResponse toResponse(License l) {
        String username = null;
        if (l.getAssignedToUserId() != null) {
            username = userRepository.findById(l.getAssignedToUserId())
                    .map(User::getUsername).orElse(null);
        }
        return LicenseResponse.builder()
                .id(l.getId())
                .code(l.getCode())
                .status(l.getStatus())
                .assignedToUserId(l.getAssignedToUserId())
                .assignedToUsername(username)
                .assignedAt(l.getAssignedAt())
                .assignedByUserId(l.getAssignedByUserId())
                .expiresAt(l.getExpiresAt())
                .revokedAt(l.getRevokedAt())
                .revokedByUserId(l.getRevokedByUserId())
                .notes(l.getNotes())
                .createdAt(l.getCreatedAt())
                .build();
    }

    private Long currentUserIdOrNull() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        return userRepository.findByUsernameAndIsDeletedFalse(auth.getName())
                .map(User::getId)
                .orElse(null);
    }
}
