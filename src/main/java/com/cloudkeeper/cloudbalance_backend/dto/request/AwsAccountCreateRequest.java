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
    @NotBlank(message = "Account ID is required")
    @Pattern(regexp = AWS_ACCOUNT_ID, message = "Account ID must be exactly 12 digits")
    @SafeInput
    private String accountId;

    @NotBlank(message = "Account alias is required")
    @Pattern(regexp = ALPHANUMERIC_SPACE_DASH, message = "Account alias can only contain letters, numbers, spaces, hyphens, and underscores.")
    @SafeInput
    private String accountAlias;

    @Pattern(regexp = AWS_ROLE_ARN, message = "Invalid IAM Role ARN format")
    @SafeInput
    private String roleArn;
}
