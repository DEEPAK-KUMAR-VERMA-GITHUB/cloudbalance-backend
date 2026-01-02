package com.cloudkeeper.cloudbalance_backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AwsAccountCreateRequest {
    @NotBlank(message = "Account id is required")
    private String accountId;
    @NotBlank(message = "Account alias is required")
    private String accountAlias;
    @NotBlank(message = "Access key id is required.")
    private String accessKeyId;
    @NotBlank(message = "Secret Access key is required.")
    private String secretAccessKey;
    @NotBlank(message = "Region is required.")
    private String region;

    private BigDecimal monthlyBudget;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
