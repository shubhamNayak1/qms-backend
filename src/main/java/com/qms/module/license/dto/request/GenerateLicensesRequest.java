package com.qms.module.license.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "Generate N new licenses into the AVAILABLE pool")
public class GenerateLicensesRequest {

    @NotNull
    @Min(1) @Max(500)
    @Schema(example = "10",
            description = "How many licenses to mint. Capped at 500 per call to keep the audit log readable.")
    private Integer count;

    /** Optional bulk expiry — applied to every minted license. Null = perpetual. */
    private LocalDateTime expiresAt;

    /** Optional free-text note attached to the batch (e.g. PO number). */
    private String notes;
}
