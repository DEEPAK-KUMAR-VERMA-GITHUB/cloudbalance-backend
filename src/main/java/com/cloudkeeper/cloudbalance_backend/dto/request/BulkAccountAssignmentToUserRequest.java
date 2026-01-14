package com.cloudkeeper.cloudbalance_backend.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkAccountAssignmentToUserRequest {

    @NotNull(message = "User ID cannot be null")
    private Long userId;

    @NotEmpty(message = "Account IDs list cannot be empty")
    private List<Long> accountIds;
}
