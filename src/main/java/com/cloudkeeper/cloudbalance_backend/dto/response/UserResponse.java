package com.cloudkeeper.cloudbalance_backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@Builder
public class UserResponse {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private Set<String> roles;
    private Boolean active;
    private LocalDateTime lastLogin;
    private LocalDateTime createdAt;
    private Boolean canPromote;
    private Boolean canResend;
}
