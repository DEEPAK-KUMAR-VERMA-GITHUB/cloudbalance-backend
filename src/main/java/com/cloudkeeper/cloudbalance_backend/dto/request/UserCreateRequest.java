package com.cloudkeeper.cloudbalance_backend.dto.request;

import com.cloudkeeper.cloudbalance_backend.helper.validations.SafeInput;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.util.Set;

import static com.cloudkeeper.cloudbalance_backend.helper.validations.ValidationPatterns.MSG_ALPHANUMERIC;
import static com.cloudkeeper.cloudbalance_backend.helper.validations.ValidationPatterns.PERSON_NAME;

@Data
public class UserCreateRequest {
    @NotBlank(message = "First name is required")
    @Length(min = 2, max = 25, message = "First name must be between 2 and 25 characters")
    @Pattern(regexp = PERSON_NAME, message = MSG_ALPHANUMERIC)
    @SafeInput
    private String firstName;

    @Length(max = 25, message = "Last name must not exceed 25 characters")
    @Pattern(regexp = PERSON_NAME, message = MSG_ALPHANUMERIC)
    @SafeInput
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email")
    @SafeInput
    private String email;

    @NotEmpty(message = "At least one role is required")
    private String role;  // converted to userrole enum in user service
}
