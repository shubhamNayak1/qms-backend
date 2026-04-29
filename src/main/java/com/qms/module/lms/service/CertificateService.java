package com.qms.module.lms.service;

import com.qms.common.exception.AppException;
import com.qms.common.response.PageResponse;
import com.qms.module.lms.dto.response.CertificateResponse;
import com.qms.module.lms.entity.Enrollment;
import com.qms.module.lms.entity.TrainingCertificate;
import com.qms.module.lms.enums.CertificateStatus;
import com.qms.module.lms.repository.TrainingCertificateRepository;
import com.qms.common.enums.AuditAction;
import com.qms.common.enums.AuditModule;
import com.qms.module.audit.annotation.Audited;
import com.qms.module.audit.context.AuditContext;
import com.qms.module.audit.context.AuditContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CertificateService {

    private final TrainingCertificateRepository certificateRepository;

    @Value("${lms.certificate.issuer:QMS Quality Assurance Department}")
    private String issuer;

    // ── Queries ──────────────────────────────────────────────

    public PageResponse<CertificateResponse> getAll(int page, int size, CertificateStatus status) {
        PageRequest pr = PageRequest.of(page, size, Sort.by("issuedDate").descending());
        return PageResponse.of(
                status != null
                        ? certificateRepository.findByStatusOrderByIssuedDateDesc(status, pr).map(this::toResponse)
                        : certificateRepository.findAllByOrderByIssuedDateDesc(pr).map(this::toResponse));
    }

    public PageResponse<CertificateResponse> getByUser(Long userId, int page, int size) {
        return PageResponse.of(
                certificateRepository.findByUserIdOrderByIssuedDateDesc(userId,
                        PageRequest.of(page, size, Sort.by("issuedDate").descending()))
                        .map(this::toResponse));
    }

    public CertificateResponse getByNumber(String certificateNumber) {
        return toResponse(certificateRepository.findByCertificateNumber(certificateNumber)
                .orElseThrow(() -> AppException.notFound(
                        "Certificate with number: " + certificateNumber)));
    }

    public CertificateResponse getByEnrollment(Long enrollmentId) {
        return toResponse(certificateRepository.findByEnrollment_Id(enrollmentId)
                .orElseThrow(() -> AppException.notFound("Certificate for enrollment", enrollmentId)));
    }

    // ── Issue (called by EnrollmentService on completion) ────

    @Audited(action = AuditAction.CREATE, module = AuditModule.TRAINING, entityType = "TrainingCertificate",
             description = "Training certificate issued on enrollment completion")
    @Transactional
    public void issue(Enrollment enrollment, Integer score) {
        // Idempotent — don't issue twice for the same enrollment
        certificateRepository.findByEnrollment_Id(enrollment.getId()).ifPresent(existing -> {
            log.warn("Certificate already exists for enrollment {}", enrollment.getId());
            return;
        });

        if (certificateRepository.findByEnrollment_Id(enrollment.getId()).isPresent()) return;

        LocalDate today   = LocalDate.now();
        int validityYears = enrollment.getProgram().getCertificateValidityYears() != null
                ? enrollment.getProgram().getCertificateValidityYears() : 2;
        LocalDate expiry  = today.plusYears(validityYears);

        String certNumber = buildCertNumber(enrollment);

        TrainingCertificate cert = TrainingCertificate.builder()
                .enrollment(enrollment)
                .userId(enrollment.getUserId())
                .userName(enrollment.getUserName())
                .programId(enrollment.getProgram().getId())
                .programTitle(enrollment.getProgram().getTitle())
                .programCode(enrollment.getProgram().getCode())
                .certificateNumber(certNumber)
                .issuer(issuer)
                .issuedDate(today)
                .expiryDate(expiry)
                .status(CertificateStatus.ACTIVE)
                .scoreAchieved(score)
                .build();

        certificateRepository.save(cert);
        log.info("Certificate issued: {} for userId={} programId={}",
                certNumber, enrollment.getUserId(), enrollment.getProgram().getId());
    }

    // ── Revoke ───────────────────────────────────────────────

    @Audited(action = AuditAction.UPDATE, module = AuditModule.TRAINING, entityType = "TrainingCertificate",
             entityIdArgIndex = 0, captureOldValue = true, description = "Training certificate revoked")
    @Transactional
    public CertificateResponse revoke(Long id, String reason) {
        TrainingCertificate cert = certificateRepository.findById(id)
                .orElseThrow(() -> AppException.notFound("Certificate", id));
        AuditContextHolder.set(AuditContext.builder()
                .entityId(id)
                .description("Certificate " + cert.getCertificateNumber() + " revoked — reason: " + reason)
                .build());
        if (cert.getStatus() == CertificateStatus.REVOKED) {
            throw AppException.badRequest("Certificate is already revoked");
        }
        cert.setStatus(CertificateStatus.REVOKED);
        cert.setRevokedReason(reason);
        cert.setRevokedBy(currentUsername());
        cert.setRevokedAt(java.time.LocalDateTime.now());
        log.info("Certificate {} revoked by {}", cert.getCertificateNumber(), currentUsername());
        return toResponse(certificateRepository.save(cert));
    }

    // ── Expiry (called by scheduler) ─────────────────────────

    @Transactional
    public void expireLapsed() {
        List<TrainingCertificate> lapsed = certificateRepository.findLapsed(LocalDate.now());
        if (lapsed.isEmpty()) return;
        lapsed.forEach(c -> {
            c.setStatus(CertificateStatus.EXPIRED);
            if (c.getEnrollment() != null) {
                c.getEnrollment().setStatus(
                        com.qms.module.lms.enums.EnrollmentStatus.EXPIRED);
            }
        });
        certificateRepository.saveAll(lapsed);
        log.info("Expired {} lapsed training certificates", lapsed.size());
    }

    public List<CertificateResponse> getExpiringSoon(int warningDays) {
        LocalDate today   = LocalDate.now();
        LocalDate warning = today.plusDays(warningDays);
        return certificateRepository.findExpiringSoon(today, warning)
                .stream().map(this::toResponse).toList();
    }

    // ── Helpers ──────────────────────────────────────────────

    private String buildCertNumber(Enrollment enrollment) {
        String month = DateTimeFormatter.ofPattern("yyyyMM").format(LocalDate.now());
        return "CERT-" + enrollment.getProgram().getCode()
                + "-" + enrollment.getUserId()
                + "-" + month;
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "SYSTEM";
    }

    CertificateResponse toResponse(TrainingCertificate c) {
        return CertificateResponse.builder()
                .id(c.getId())
                .certificateNumber(c.getCertificateNumber())
                .userId(c.getUserId()).userName(c.getUserName())
                .programId(c.getProgramId()).programCode(c.getProgramCode())
                .programTitle(c.getProgramTitle())
                .issuer(c.getIssuer())
                .issuedDate(c.getIssuedDate()).expiryDate(c.getExpiryDate())
                .status(c.getStatus()).scoreAchieved(c.getScoreAchieved())
                .revokedReason(c.getRevokedReason())
                .createdAt(c.getCreatedAt())
                .expired(c.isExpired())
                .build();
    }
}
