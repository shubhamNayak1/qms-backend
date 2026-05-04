package com.qms.module.qms.common.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * Create or update a "Existing System / Proposed System / Justification"
 * row attached to a QMS record. Used uniformly by every sub-module.
 */
@Data
@Schema(description = "QMS line item — repeating row on every record")
public class QmsLineItemRequest {

    @Size(max = 5000)
    private String existingSystem;

    @Size(max = 5000)
    private String proposedSystem;

    @Size(max = 5000)
    private String justification;

    /** Optional — defaults to today on create when null. */
    private LocalDate proposedDate;

    /** Verification phase fields, used after the change is implemented. */
    @Size(max = 30)
    private String status;

    @Size(max = 5000)
    private String remark;
}
