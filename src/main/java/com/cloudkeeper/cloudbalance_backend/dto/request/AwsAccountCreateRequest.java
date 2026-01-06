package com.cloudkeeper.cloudbalance_backend.dto.request;

import static com.cloudkeeper.cloudbalance_backend.helper.validations.ValidationPatterns.*;

import com.cloudkeeper.cloudbalance_backend.helper.validations.SafeInput;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.hibernate.validator.constraints.Length;
import org.springframework.security.core.parameters.P;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AwsAccountCreateRequest {
    @NotBlank(message = "Account id is required")
    @Pattern(regexp = AWS_ACCOUNT_ID, message = "Account ID must be exactly 12 digits")
    @SafeInput
    private String accountId;

    @NotBlank(message = "Account alias is required")
    @Pattern(regexp = ALPHANUMERIC_SPACE_DASH, message = "Account alias can only contain letters, numbers, spaces, hyphens, and underscores.")
    @SafeInput
    private String accountAlias;

    @NotBlank(message = "Access key id is required.")
    @Length(max = 100)
    @Pattern(regexp = AWS_ACCESS_KEY, message = "Invalid AWS Access key ID format.")
    @SafeInput
    private String accessKeyId;

    @NotBlank(message = "Secret Access key is required.")
    @Length(max = 100)
    @Pattern(regexp = AWS_SECRET_KEY, message = "Invalid AWS Secret access key format.")
    @SafeInput
    private String secretAccessKey;

    @NotBlank(message = "Region is required")
    @Pattern(regexp = AWS_REGION, message = "Invalid AWS region format (e.g., us-east-1)")
    private String region;

    @DecimalMin(value = "0.0", inclusive = false, message = "Monthly budget must be positive")
    private BigDecimal monthlyBudget;

    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
