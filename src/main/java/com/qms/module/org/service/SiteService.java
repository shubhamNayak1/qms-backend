package com.qms.module.org.service;

import com.qms.common.enums.AuditAction;
import com.qms.common.enums.AuditModule;
import com.qms.common.exception.AppException;
import com.qms.module.audit.annotation.Audited;
import com.qms.module.org.dto.request.SiteRequest;
import com.qms.module.org.dto.response.SiteResponse;
import com.qms.module.org.entity.Site;
import com.qms.module.org.repository.SiteRepository;
import com.qms.module.user.entity.User;
import com.qms.module.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SiteService {

    private final SiteRepository siteRepository;
    private final UserRepository userRepository;

    public SiteResponse getDefault() {
        Site s = siteRepository.findFirstByIsDeletedFalseOrderByIdAsc()
                .orElseThrow(() -> AppException.internalError(
                        "No active site is configured. Run V18 migration."));
        return toResponse(s);
    }

    @Audited(action = AuditAction.UPDATE, module = AuditModule.ORG,
             entityType = "Site", entityIdArgIndex = 0,
             description = "Site profile updated (name / address / Site Head)")
    @Transactional
    public SiteResponse update(Long id, SiteRequest req) {
        Site s = siteRepository.findById(id)
                .orElseThrow(() -> AppException.notFound("Site", id));

        // Validate the proposed head exists when supplied.
        if (req.getHeadUserId() != null) {
            userRepository.findByIdAndIsDeletedFalse(req.getHeadUserId())
                    .orElseThrow(() -> AppException.notFound("User", req.getHeadUserId()));
        }

        s.setName(req.getName());
        s.setCode(req.getCode());
        s.setAddress(req.getAddress());
        s.setHeadUserId(req.getHeadUserId());
        Site saved = siteRepository.save(s);
        log.info("Site id={} updated — head={}", saved.getId(), saved.getHeadUserId());
        return toResponse(saved);
    }

    private SiteResponse toResponse(Site s) {
        String headName = null;
        if (s.getHeadUserId() != null) {
            headName = userRepository.findById(s.getHeadUserId())
                    .map(User::getFullName).orElse(null);
        }
        return SiteResponse.builder()
                .id(s.getId())
                .name(s.getName())
                .code(s.getCode())
                .address(s.getAddress())
                .headUserId(s.getHeadUserId())
                .headUserName(headName)
                .isActive(s.getIsActive())
                .createdAt(s.getCreatedAt())
                .build();
    }
}
