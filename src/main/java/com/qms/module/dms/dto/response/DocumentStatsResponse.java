package com.qms.module.dms.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class DocumentStatsResponse {

    private LocalDateTime generatedAt;

    // Counts by status
    private long draftCount;
    private long underReviewCount;
    private long approvedCount;
    private long effectiveCount;
    private long obsoleteCount;
    private long totalCount;

    // Attention items
    private long expiringSoonCount;
    private long expiredCount;
    private long dueForReviewCount;

    // Lists for action dashboards
    private List<DocumentResponse> expiringSoon;
    private List<DocumentResponse> dueForReview;
}
