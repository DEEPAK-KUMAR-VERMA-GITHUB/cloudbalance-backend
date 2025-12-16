package com.cloudkeeper.cloudbalance_backend.dto.request;

import com.cloudkeeper.cloudbalance_backend.entity.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.util.Set;

@Data
public class UserCreateRequest {
    @NotBlank(message = "First name is required")
    @Length(max = 25    , message = "First name must be less than 25 characters")
    private String firstName;

    @Length(max = 25, message = "Last name must be less than 25 characters")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email")
    private String email;

    @NotEmpty(message = "At least one role is required")
    private Set<String> roles;  // converted to userrole enum in user service
}
