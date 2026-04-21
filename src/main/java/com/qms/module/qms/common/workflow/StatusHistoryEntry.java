package com.qms.module.qms.common.workflow;

import com.qms.common.enums.QmsStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A single entry in a record's status history log.
 * Serialized to JSON and stored in qms_record.status_history (TEXT column).
 * Immutable after creation — never modify existing entries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusHistoryEntry {

    private QmsStatus     fromStatus;
    private QmsStatus     toStatus;
    private String        changedByUsername;
    private Long          changedByUserId;
    private String        comment;
    private LocalDateTime changedAt;

    public static StatusHistoryEntry of(QmsStatus from, QmsStatus to,
                                         String username, Long userId,
                                         String comment) {
        return StatusHistoryEntry.builder()
                .fromStatus(from)
                .toStatus(to)
                .changedByUsername(username)
                .changedByUserId(userId)
                .comment(comment)
                .changedAt(LocalDateTime.now())
                .build();
    }
}
