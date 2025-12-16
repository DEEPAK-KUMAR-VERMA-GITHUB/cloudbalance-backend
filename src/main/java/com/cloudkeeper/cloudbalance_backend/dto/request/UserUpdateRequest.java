package com.cloudkeeper.cloudbalance_backend.dto.request;

import lombok.Data;

import java.util.Set;

@Data
public class UserUpdateRequest {
    private String firstName;
    private String lastName;
    private Set<String> roles;
    private Boolean active;
}
