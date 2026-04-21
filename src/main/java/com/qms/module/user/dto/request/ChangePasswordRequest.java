package com.qms.module.user.dto.request;

import com.qms.module.user.validator.ValidPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Change password — requires current password verification")
public class ChangePasswordRequest {

    @NotBlank(message = "Current password is required")
    @Schema(example = "OldPass@123")
    private String currentPassword;

    @NotBlank(message = "New password is required")
    @ValidPassword
    @Schema(example = "NewPass@456")
    private String newPassword;

    @NotBlank(message = "Confirm password is required")
    @Schema(example = "NewPass@456")
    private String confirmPassword;
}
