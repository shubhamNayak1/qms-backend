package com.qms.module.license.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LicenseStatsResponse {
    private long total;
    private long available;
    private long assigned;
    private long revoked;
    private long expired;
}
