package com.cloudkeeper.cloudbalance_backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountAssignmentRequest {
    @NotBlank(message = "AWS account id is required.")
    private Long awsAccountId;
    @NotBlank(message = "User id is required.")
    private Long userId;
}