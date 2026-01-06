package com.cloudkeeper.cloudbalance_backend.dto.request;

import static com.cloudkeeper.cloudbalance_backend.helper.validations.ValidationPatterns.*;

import com.cloudkeeper.cloudbalance_backend.helper.validations.SafeInput;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.util.Set;

@Data
public class UserUpdateRequest {
    @NotBlank(message = "First name is required")
    @Length(min = 2, max = 25, message = "First name must be between 2 to 25 characters")
    @Pattern(regexp = PERSON_NAME, message = MSG_PERSON_NAME)
    @SafeInput
    private String firstName;

    @Length(max = 25, message = "Last name must not exceed 25 characters")
    @Pattern(regexp = PERSON_NAME, message = MSG_PERSON_NAME)
    @SafeInput
    private String lastName;

    @NotEmpty(message = "At least one role is required")
    private Set<String> roles;

    @NotBlank(message = "Status is required.")
    private Boolean active;
}
