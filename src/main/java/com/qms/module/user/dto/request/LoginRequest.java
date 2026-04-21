package com.qms.module.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Login credentials — accepts username OR email")
public class LoginRequest {

    @NotBlank(message = "Username or email is required")
    @Schema(example = "john.doe", description = "Username or registered email address")
    private String usernameOrEmail;

    @NotBlank(message = "Password is required")
    @Schema(example = "SecurePass@123")
    private String password;
}
