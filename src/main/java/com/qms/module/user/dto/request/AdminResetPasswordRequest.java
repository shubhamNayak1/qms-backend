package com.qms.module.user.dto.request;

import com.qms.module.user.validator.ValidPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request body for an administrator forcibly resetting another user's password.
 * The target user is identified by the path variable on the controller; the new
 * password must satisfy the active password policy.
 *
 * After a successful reset the user is forced to change the password on next
 * login (mustChangePassword = true).
 */
@Data
@Schema(description = "Admin-initiated password reset for another user")
public class AdminResetPasswordRequest {

    @NotBlank(message = "New password is required")
    @ValidPassword
    @Schema(example = "TempPass@2025",
            description = "Temporary password — must satisfy the active password policy")
    private String newPassword;
}
