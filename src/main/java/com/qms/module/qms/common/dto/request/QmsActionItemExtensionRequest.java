package com.qms.module.qms.common.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Batch C RED-5 (2026-07-19): dept-declared extension of an overdue
 * {@code QmsDepartmentActionItem}'s target date. The dept HOD records
 * this before they can upload evidence against an overdue action item.
 */
@Getter
@Setter
@Schema(description = "Extension of an action item's target date")
public class QmsActionItemExtensionRequest {

    @Schema(description = "New effective deadline — must be today or later", required = true)
    private LocalDate extensionDate;

    @Schema(description = "Why the extension is needed (optional)")
    private String extensionReason;
}
