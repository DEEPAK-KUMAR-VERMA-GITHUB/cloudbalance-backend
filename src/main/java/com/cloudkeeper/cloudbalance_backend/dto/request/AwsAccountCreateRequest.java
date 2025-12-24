package com.cloudkeeper.cloudbalance_backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AwsAccountCreateRequest {
    @NotBlank(message = "Account id is required")
    private String accountId;
    private String accountAlias;
    private String accessKeyId;
    private String secretAccessKey;
    private String region;
    private BigDecimal monthlyBudget;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
