package com.cloudkeeper.cloudbalance_backend.dto.response;

import com.cloudkeeper.cloudbalance_backend.entity.UserSessionRedis;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AuthResponse {
    private String accessToken;
    private String sessionId;
    private String type;
    private String name;
    private String email;
    private String role;
    private Long expiresIn;
    private Boolean hasActiveSession;
    private String message;  // error or warning messages
    private List<UserSessionRedis> activeSessions; // list of active sessions
}
