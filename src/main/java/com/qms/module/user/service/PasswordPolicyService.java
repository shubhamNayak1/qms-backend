package com.qms.module.user.service;

import com.qms.common.exception.AppException;
import com.qms.module.user.dto.request.PasswordPolicyRequest;
import com.qms.module.user.dto.response.PasswordPolicyResponse;
import com.qms.module.user.entity.PasswordHistory;
import com.qms.module.user.entity.PasswordPolicy;
import com.qms.module.user.repository.PasswordHistoryRepository;
import com.qms.module.user.repository.PasswordPolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PasswordPolicyService {

    private final PasswordPolicyRepository  policyRepository;
    private final PasswordHistoryRepository historyRepository;
    private final PasswordEncoder           passwordEncoder;

    // ─── Queries ─────────────────────────────────────────────

    /**
     * Returns the currently active policy (effectiveDate ≤ today).
     * Falls back to a sensible default if no policy has been defined yet.
     */
    public PasswordPolicyResponse getActivePolicy() {
        return policyRepository.findActivePolicy(LocalDate.now())
                .map(p -> toResponse(p, true))
                .orElseGet(this::defaultPolicyResponse);
    }

    /** Returns all non-deleted policies, newest first. */
    public List<PasswordPolicyResponse> getAllPolicies() {
        LocalDate today = LocalDate.now();
        return policyRepository.findAllActive().stream()
                .map(p -> toResponse(p, !p.getEffectiveDate().isAfter(today)))
                .toList();
    }

    public PasswordPolicyResponse getById(Long id) {
        PasswordPolicy p = findById(id);
        return toResponse(p, !p.getEffectiveDate().isAfter(LocalDate.now()));
    }

    // ─── Commands ────────────────────────────────────────────

    @Transactional
    public PasswordPolicyResponse create(PasswordPolicyRequest req) {
        validateRequest(req, null);

        PasswordPolicy policy = PasswordPolicy.builder()
                .passwordLengthMin(req.getPasswordLengthMin())
                .passwordLengthMax(req.getPasswordLengthMax())
                .alphaMin(req.getAlphaMin())
                .numericMin(req.getNumericMin())
                .specialCharMin(req.getSpecialCharMin())
                .upperCaseMin(req.getUpperCaseMin())
                .numberOfLoginAttempts(req.getNumberOfLoginAttempts())
                .validPeriod(req.getValidPeriod())
                .previousPasswordAttemptTrack(req.getPreviousPasswordAttemptTrack())
                .effectiveDate(req.getEffectiveDate())
                .build();

        PasswordPolicy saved = policyRepository.save(policy);
        log.info("Password policy created: id={}, effectiveDate={}", saved.getId(), saved.getEffectiveDate());
        return toResponse(saved, !saved.getEffectiveDate().isAfter(LocalDate.now()));
    }

    @Transactional
    public PasswordPolicyResponse update(Long id, PasswordPolicyRequest req) {
        PasswordPolicy policy = findById(id);
        validateRequest(req, id);

        policy.setPasswordLengthMin(req.getPasswordLengthMin());
        policy.setPasswordLengthMax(req.getPasswordLengthMax());
        policy.setAlphaMin(req.getAlphaMin());
        policy.setNumericMin(req.getNumericMin());
        policy.setSpecialCharMin(req.getSpecialCharMin());
        policy.setUpperCaseMin(req.getUpperCaseMin());
        policy.setNumberOfLoginAttempts(req.getNumberOfLoginAttempts());
        policy.setValidPeriod(req.getValidPeriod());
        policy.setPreviousPasswordAttemptTrack(req.getPreviousPasswordAttemptTrack());
        policy.setEffectiveDate(req.getEffectiveDate());

        PasswordPolicy saved = policyRepository.save(policy);
        log.info("Password policy updated: id={}", saved.getId());
        return toResponse(saved, !saved.getEffectiveDate().isAfter(LocalDate.now()));
    }

    @Transactional
    public void delete(Long id) {
        PasswordPolicy policy = findById(id);
        policy.setIsDeleted(true);
        policyRepository.save(policy);
        log.info("Password policy soft-deleted: id={}", id);
    }

    // ─── Password enforcement ─────────────────────────────────

    /**
     * Validates {@code plainPassword} against the currently active policy.
     * Throws {@link AppException} with a descriptive message if any rule fails.
     */
    public void enforcePolicy(String plainPassword) {
        policyRepository.findActivePolicy(LocalDate.now())
                .ifPresent(policy -> validate(plainPassword, policy));
    }

    /**
     * Checks that {@code plainPassword} is not one of the user's last N passwords.
     * N = policy.previousPasswordAttemptTrack.  No-op if N = 0 or no active policy.
     */
    public void enforcePasswordHistory(Long userId, String plainPassword) {
        policyRepository.findActivePolicy(LocalDate.now()).ifPresent(policy -> {
            int track = policy.getPreviousPasswordAttemptTrack();
            if (track <= 0) return;

            List<PasswordHistory> recent = historyRepository.findRecentByUserId(userId, track);
            boolean reused = recent.stream()
                    .anyMatch(h -> passwordEncoder.matches(plainPassword, h.getPasswordHash()));
            if (reused) {
                throw AppException.badRequest(
                        "You cannot reuse any of your last " + track + " passwords.");
            }
        });
    }

    /**
     * Saves the given BCrypt hash to the user's password history and prunes
     * rows beyond the configured limit.
     */
    @Transactional
    public void recordPasswordHistory(Long userId, String bcryptHash) {
        PasswordHistory entry = PasswordHistory.builder()
                .userId(userId)
                .passwordHash(bcryptHash)
                .build();
        historyRepository.save(entry);

        // Prune: keep at most max(previousPasswordAttemptTrack, 10) rows
        int keep = policyRepository.findActivePolicy(LocalDate.now())
                .map(p -> Math.max(p.getPreviousPasswordAttemptTrack(), 1))
                .orElse(10);
        historyRepository.pruneOldHistory(userId, keep);
    }

    /**
     * Returns the validPeriod from the active policy (0 = never expire).
     */
    public int getActiveValidPeriod() {
        return policyRepository.findActivePolicy(LocalDate.now())
                .map(PasswordPolicy::getValidPeriod)
                .orElse(0);
    }

    // ─── Internal helpers ────────────────────────────────────

    private PasswordPolicy findById(Long id) {
        return policyRepository.findById(id)
                .filter(p -> !Boolean.TRUE.equals(p.getIsDeleted()))
                .orElseThrow(() -> AppException.notFound("PasswordPolicy", id));
    }

    private void validateRequest(PasswordPolicyRequest req, Long excludeId) {
        if (req.getPasswordLengthMin() > req.getPasswordLengthMax()) {
            throw AppException.badRequest(
                    "passwordLengthMin (" + req.getPasswordLengthMin() +
                    ") cannot be greater than passwordLengthMax (" + req.getPasswordLengthMax() + ").");
        }

        boolean dateConflict = excludeId == null
                ? policyRepository.existsByEffectiveDateAndIsDeletedFalse(req.getEffectiveDate())
                : policyRepository.existsByEffectiveDateAndIsDeletedFalseAndIdNot(req.getEffectiveDate(), excludeId);

        if (dateConflict) {
            throw AppException.conflict(
                    "A policy with effectiveDate " + req.getEffectiveDate() + " already exists.");
        }
    }

    private void validate(String password, PasswordPolicy policy) {
        if (password == null) return;

        int len = password.length();

        if (len < policy.getPasswordLengthMin()) {
            throw AppException.badRequest(
                    "Password must be at least " + policy.getPasswordLengthMin() + " characters long.");
        }
        if (policy.getPasswordLengthMax() > 0 && len > policy.getPasswordLengthMax()) {
            throw AppException.badRequest(
                    "Password must be at most " + policy.getPasswordLengthMax() + " characters long.");
        }

        long alphaCount   = password.chars().filter(Character::isLetter).count();
        long digitCount   = password.chars().filter(Character::isDigit).count();
        long upperCount   = password.chars().filter(Character::isUpperCase).count();
        long specialCount = password.chars()
                .filter(c -> !Character.isLetterOrDigit(c))
                .count();

        if (alphaCount < policy.getAlphaMin()) {
            throw AppException.badRequest(
                    "Password must contain at least " + policy.getAlphaMin() + " letter(s).");
        }
        if (digitCount < policy.getNumericMin()) {
            throw AppException.badRequest(
                    "Password must contain at least " + policy.getNumericMin() + " number(s).");
        }
        if (upperCount < policy.getUpperCaseMin()) {
            throw AppException.badRequest(
                    "Password must contain at least " + policy.getUpperCaseMin() + " uppercase letter(s).");
        }
        if (specialCount < policy.getSpecialCharMin()) {
            throw AppException.badRequest(
                    "Password must contain at least " + policy.getSpecialCharMin() + " special character(s).");
        }
    }

    private PasswordPolicyResponse toResponse(PasswordPolicy p, boolean isActive) {
        return PasswordPolicyResponse.builder()
                .id(p.getId())
                .passwordLengthMin(p.getPasswordLengthMin())
                .passwordLengthMax(p.getPasswordLengthMax())
                .alphaMin(p.getAlphaMin())
                .numericMin(p.getNumericMin())
                .specialCharMin(p.getSpecialCharMin())
                .upperCaseMin(p.getUpperCaseMin())
                .numberOfLoginAttempts(p.getNumberOfLoginAttempts())
                .validPeriod(p.getValidPeriod())
                .previousPasswordAttemptTrack(p.getPreviousPasswordAttemptTrack())
                .effectiveDate(p.getEffectiveDate())
                .isActive(isActive)
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .createdBy(p.getCreatedBy())
                .build();
    }

    private PasswordPolicyResponse defaultPolicyResponse() {
        return PasswordPolicyResponse.builder()
                .id(null)
                .passwordLengthMin(8)
                .passwordLengthMax(128)
                .alphaMin(1)
                .numericMin(1)
                .specialCharMin(1)
                .upperCaseMin(1)
                .numberOfLoginAttempts(5)
                .validPeriod(0)
                .previousPasswordAttemptTrack(0)
                .effectiveDate(null)
                .isActive(true)
                .build();
    }
}
