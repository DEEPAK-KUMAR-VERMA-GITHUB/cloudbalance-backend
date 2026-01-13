package com.cloudkeeper.cloudbalance_backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AwsAccountResponse {
    private Long id;
    private String accountId;
    private String accountAlias;
    private String roleArn;
    private Boolean active;
    private Integer assignedUsersCount;
    private Set<String> assignedUserEmails;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
