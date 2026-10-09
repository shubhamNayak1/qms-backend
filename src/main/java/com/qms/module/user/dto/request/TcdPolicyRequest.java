package com.qms.module.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
@Schema(description = "Request body for updating a TCD policy row")
public class TcdPolicyRequest {

    @Min(value = 1, message = "maxDays must be at least 1")
    @Schema(description = "Maximum days from initiate to close", example = "30")
    private int maxDays;

    @Min(value = 0, message = "warningDays cannot be negative")
    @Schema(description = "Days before due date when the row flips amber", example = "5")
    private int warningDays;

    @Schema(description = "Whether a target-date extension can be requested", example = "true")
    private boolean extensionAllowed;

    @Min(value = 0, message = "maxExtensions cannot be negative")
    @Schema(description = "Maximum extensions permitted per record", example = "1")
    private int maxExtensions;
}
