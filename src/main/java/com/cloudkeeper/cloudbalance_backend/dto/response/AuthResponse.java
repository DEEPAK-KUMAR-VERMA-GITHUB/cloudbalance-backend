package com.cloudkeeper.cloudbalance_backend.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String type;
    private String name;
    private String email;
    private String role;
    private Long expiresIn;
    private Boolean hasActiveSession;
}
