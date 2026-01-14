package com.cloudkeeper.cloudbalance_backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkAccountAssignmentResponse {
    private Long userId;
    private String userEmail;
    private Integer totalAccountsRequested;
    private Integer successfulAssignments;
    private Integer skippedDuplicates;
    private Integer failedAssignments;
    private List<AccountAssignmentResult> results;
}