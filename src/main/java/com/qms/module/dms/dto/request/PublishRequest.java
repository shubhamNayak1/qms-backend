package com.qms.module.dms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
@Schema(description = "Publish an APPROVED document, making it EFFECTIVE")
public class PublishRequest {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Schema(description = "Effective date — defaults to today if not specified", example = "2024-05-01")
    private LocalDate effectiveDate;
}
