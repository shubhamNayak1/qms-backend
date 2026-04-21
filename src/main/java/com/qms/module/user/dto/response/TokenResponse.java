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
}
