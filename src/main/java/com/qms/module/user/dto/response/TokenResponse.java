package com.qms.module.user.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.Set;

@Data
@Builder
public class TokenResponse {
    private String  accessToken;
    private String  refreshToken;
    private String  tokenType;        // always "Bearer"
    private Long    accessExpiresIn;  // seconds
    private Long    refreshExpiresIn; // seconds
    private Long    userId;
    private String  username;
    private String  email;
    private String  fullName;
    private Set<String> roles;
    private Set<String> permissions;

    /**
     * True when the user must change their password before proceeding.
     * This happens on first login (account was just created) or after
     * an admin has reset the password.
     * The frontend should redirect to the change-password page immediately.
     */
    private boolean mustChangePassword;
}
