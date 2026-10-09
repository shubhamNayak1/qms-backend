package com.qms.module.user.service;

import com.qms.common.exception.AppException;
import com.qms.module.user.dto.request.TcdPolicyRequest;
import com.qms.module.user.dto.response.TcdPolicyResponse;
import com.qms.module.user.entity.TcdPolicy;
import com.qms.module.user.repository.TcdPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * TcdPolicyService — list + update endpoints for the Target Closure Days
 * configuration surfaced on the Admin → Policy tab.
 *
 * There is no create / delete surface: the policy rows are seeded via
 * V34 and represent the five QMS modules. If a module is added later,
 * its row goes in via a new Flyway migration, not an API call.
 */
@Service
@RequiredArgsConstructor
public class TcdPolicyService {

    private final TcdPolicyRepository repository;

    @Transactional(readOnly = true)
    public List<TcdPolicyResponse> list() {
        return repository.findAllActive().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TcdPolicyResponse getByModule(String moduleKey) {
        return repository.findByModuleKeyAndIsDeletedFalse(moduleKey)
                .map(this::toResponse)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "No TCD policy row for module: " + moduleKey));
    }

    @Transactional
    public TcdPolicyResponse update(String moduleKey, TcdPolicyRequest req) {
        TcdPolicy row = repository.findByModuleKeyAndIsDeletedFalse(moduleKey)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "No TCD policy row for module: " + moduleKey));
        row.setMaxDays(req.getMaxDays());
        row.setWarningDays(req.getWarningDays());
        row.setExtensionAllowed(req.isExtensionAllowed());
        row.setMaxExtensions(req.getMaxExtensions());
        return toResponse(repository.save(row));
    }

    private TcdPolicyResponse toResponse(TcdPolicy p) {
        return TcdPolicyResponse.builder()
                .id(p.getId())
                .moduleKey(p.getModuleKey())
                .maxDays(p.getMaxDays())
                .warningDays(p.getWarningDays())
                .extensionAllowed(p.isExtensionAllowed())
                .maxExtensions(p.getMaxExtensions())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .createdBy(p.getCreatedBy())
                .updatedBy(p.getUpdatedBy())
                .build();
    }
}
