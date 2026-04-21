package com.qms.module.user.controller;

import com.qms.common.response.ApiResponse;
import com.qms.module.user.dto.request.*;
import com.qms.module.user.dto.response.TokenResponse;
import com.qms.module.user.service.AuthService;
import com.qms.module.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Login · Token refresh · Logout · Password reset")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    // ─────────────────────────────────────────────────────────
    // POST /api/v1/auth/login
    // ─────────────────────────────────────────────────────────
    @PostMapping("/login")
    @Operation(
        summary = "Authenticate and obtain JWT tokens",
        description = """
            Accepts username **or** email + password.
            Returns an access token (15 min) and a refresh token (7 days).
            Account is locked for 30 min after 5 consecutive failures.
            """
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
            description = "Login successful — tokens returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
            description = "Invalid credentials"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
            description = "Account locked or disabled")
    })
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok("Login successful", authService.login(request));
    }

    // ─────────────────────────────────────────────────────────
    // POST /api/v1/auth/refresh
    // ─────────────────────────────────────────────────────────
    @PostMapping("/refresh")
    @Operation(
        summary = "Rotate tokens using the refresh token",
        description = """
            Issues a new access + refresh token pair. The old refresh token is
            invalidated immediately (token rotation). Reuse of a rotated token
            invalidates all sessions for the user.
            """
    )
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.ok("Tokens refreshed", authService.refresh(request));
    }

    // ─────────────────────────────────────────────────────────
    // POST /api/v1/auth/logout
    // ─────────────────────────────────────────────────────────
    @PostMapping("/logout")
    @Operation(
        summary = "Logout — invalidate refresh token",
        description = """
            Clears the stored refresh token hash on the server side.
            The short-lived access token will expire on its own (15 min).
            The client should discard both tokens immediately.
            """
    )
    public ResponseEntity<ApiResponse<Void>> logout() {
        authService.logout();
        return ApiResponse.noContent("Logged out successfully");
    }

    // ─────────────────────────────────────────────────────────
    // POST /api/v1/auth/forgot-password
    // ─────────────────────────────────────────────────────────
    @PostMapping("/forgot-password")
    @Operation(
        summary = "Initiate forgot-password flow",
        description = """
            Generates a time-limited reset token (2 hours) and sends it to the
            registered email. Response is deliberately identical whether or not
            the email exists to prevent user enumeration.
            """
    )
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        userService.initiateForgotPassword(request.getEmail());
        return ApiResponse.noContent(
                "If that email is registered, a password reset link has been sent.");
    }

    // ─────────────────────────────────────────────────────────
    // POST /api/v1/auth/reset-password
    // ─────────────────────────────────────────────────────────
    @PostMapping("/reset-password")
    @Operation(
        summary = "Complete password reset using the emailed token",
        description = "Token must be unused and within the 2-hour validity window."
    )
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(request);
        return ApiResponse.noContent("Password reset successfully. Please log in.");
    }
}
