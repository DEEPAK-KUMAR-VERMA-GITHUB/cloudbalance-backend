package com.cloudkeeper.cloudbalance_backend.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountAssignmentRequest {
    private Long awsAccountId;
    private Long userId;
    private Boolean active;
}