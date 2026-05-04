package com.qms.module.user.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Bulk-upload result.
 *
 *   created       – number of users successfully inserted
 *   failed        – number of rows that errored (validation / duplicate)
 *   createdUsers  – ids + usernames of successful rows
 *   errors        – per-row failure reasons (zero-indexed by row position)
 */
@Data
@Builder
public class BulkUserUploadResponse {
    private int                   total;
    private int                   created;
    private int                   failed;
    private List<CreatedUserSummary> createdUsers;
    private List<RowError>           errors;

    @Data
    @Builder
    public static class CreatedUserSummary {
        private int    rowIndex;
        private Long   userId;
        private String username;
    }

    @Data
    @Builder
    public static class RowError {
        private int    rowIndex;
        private String username;     // best-effort identifier from the input
        private String message;
    }
}
