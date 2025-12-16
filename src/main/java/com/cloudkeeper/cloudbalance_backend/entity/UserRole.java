package com.cloudkeeper.cloudbalance_backend.entity;

import lombok.Getter;

@Getter
public enum UserRole {
    ADMIN("Admin"),
    READ_ONLY("Read-Only"),
    CUSTOMER("Customer");

    private final String displayName;

    UserRole(String displayName){
        this.displayName = displayName;
    }

    public static UserRole fromDisplayName(String displayName){
        for(UserRole role : UserRole.values()){
            if(role.displayName.equalsIgnoreCase((displayName))){
                return role;
            }
        }
        throw new IllegalArgumentException("Invalid role : " + displayName);
    }
}
