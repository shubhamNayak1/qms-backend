package com.qms.module.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 21 CFR Part 11 e-signature verification body (Round-2 E3).
 *
 * Sent before any QMS workflow transition (Submit, Approve, Reject, Resend,
 * Close, Cancel) to re-confirm the actor's password. Backend validates and
 * writes an audit_log entry but DOES NOT issue a new JWT — the existing
 * access token continues to authorise the subsequent workflow API call.
 */
@Data
@Schema(description = "E-signature verification body — username + password + the meaning of the signature")
public class ESignRequest {

    @NotBlank(message = "Username is required")
    @Size(max = 100)
    @Schema(description = "Must match the logged-in user's username", example = "amit.k")
    private String username;

    @NotBlank(message = "Password is required")
    @Schema(description = "User's account password (re-confirmed)", example = "********")
    private String password;

    /** The action being signed — recorded on the audit-log entry. */
    @Size(max = 200)
    @Schema(description = "Plain-English description of what is being signed",
            example = "Approve Change Control CC-202401-0023 → Forward to QA Evaluation")
    private String meaning;
}
