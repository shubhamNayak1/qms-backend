package com.qms.module.qms.common.dto.request;

import lombok.Data;
import java.time.LocalDate;

/**
 * POST payload = description + optional targetDate.
 * PUT   payload = same plus status ({@code PENDING|IN_PROGRESS|COMPLETED}).
 */
@Data
public class QmsDepartmentActionItemRequest {
    private String    description;
    private LocalDate targetDate;
    private String    status;
}
