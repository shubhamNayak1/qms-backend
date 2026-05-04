package com.qms.module.user.service;

import com.qms.module.user.dto.request.BulkUserUploadRequest;
import com.qms.module.user.dto.request.CreateUserRequest;
import com.qms.module.user.dto.response.BulkUserUploadResponse;
import com.qms.module.user.dto.response.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Bulk-creates users one row at a time. Each row runs in its own transaction
 * (REQUIRES_NEW) so a single bad row does not roll back the rest.
 *
 * No license is auto-assigned on bulk upload — admins must subsequently
 * allocate licenses to control the seat count.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BulkUserUploadService {

    private final UserService userService;

    public BulkUserUploadResponse upload(BulkUserUploadRequest req) {
        List<BulkUserUploadResponse.CreatedUserSummary> created = new ArrayList<>();
        List<BulkUserUploadResponse.RowError>           errors  = new ArrayList<>();

        boolean autoGen = Boolean.TRUE.equals(req.getAutoGeneratePasswords());

        for (int i = 0; i < req.getUsers().size(); i++) {
            CreateUserRequest row = req.getUsers().get(i);

            // Auto-generated password fallback (admin convenience).
            if (autoGen && (row.getPassword() == null || row.getPassword().isBlank())) {
                row.setPassword(defaultPasswordFor(row));
            }

            try {
                UserResponse saved = createOne(row);
                created.add(BulkUserUploadResponse.CreatedUserSummary.builder()
                        .rowIndex(i)
                        .userId(saved.getId())
                        .username(saved.getUsername())
                        .build());
            } catch (Exception ex) {
                log.warn("Bulk upload row {} failed: {}", i, ex.getMessage());
                errors.add(BulkUserUploadResponse.RowError.builder()
                        .rowIndex(i)
                        .username(row.getUsername())
                        .message(rootCauseMessage(ex))
                        .build());
            }
        }

        return BulkUserUploadResponse.builder()
                .total(req.getUsers().size())
                .created(created.size())
                .failed(errors.size())
                .createdUsers(created)
                .errors(errors)
                .build();
    }

    /**
     * Each row runs in its own transaction so a downstream policy violation
     * doesn't poison the whole batch.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UserResponse createOne(CreateUserRequest row) {
        return userService.create(row);
    }

    /**
     * Default password format: "&lt;Initials&gt;&lt;JoiningYear&gt;@123"
     * e.g. JKD2024@123 — meets the standard policy and is easy to communicate.
     * The user is forced to change it on first login (mustChangePassword=true).
     */
    private String defaultPasswordFor(CreateUserRequest row) {
        String initials = (row.getInitials() != null ? row.getInitials() : "USR").toUpperCase();
        int year = row.getJoiningDate() != null
                ? row.getJoiningDate().getYear()
                : LocalDate.now().getYear();
        return initials + year + "@123";
    }

    private String rootCauseMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) cur = cur.getCause();
        return cur.getMessage() != null ? cur.getMessage() : t.getClass().getSimpleName();
    }
}
