package com.qms.module.qms.common.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/** Initiator requests an extension of the record's target completion date. */
@Data
@Schema(description = "Request an extension of the QMS record's target completion date")
public class TargetDateExtensionRequest {

    @NotNull
    @Future(message = "Extension date must be in the future")
    private LocalDate extensionDate;

    @NotBlank(message = "Justification is required")
    @Size(max = 2000)
    private String reason;
}
