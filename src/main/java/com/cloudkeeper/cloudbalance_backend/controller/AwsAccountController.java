package com.cloudkeeper.cloudbalance_backend.controller;

import com.cloudkeeper.cloudbalance_backend.config.UserPrincipal;
import com.cloudkeeper.cloudbalance_backend.dto.request.AccountAssignmentRequest;
import com.cloudkeeper.cloudbalance_backend.dto.request.AwsAccountCreateRequest;
import com.cloudkeeper.cloudbalance_backend.dto.response.ApiResponse;
import com.cloudkeeper.cloudbalance_backend.dto.response.AwsAccountResponse;
import com.cloudkeeper.cloudbalance_backend.entity.AwsAccount;
import com.cloudkeeper.cloudbalance_backend.helper.roleAnnotations.AdminOnly;
import com.cloudkeeper.cloudbalance_backend.helper.roleAnnotations.AnyAuthenticatedUser;
import com.cloudkeeper.cloudbalance_backend.helper.roleAnnotations.CustomerOnly;
import com.cloudkeeper.cloudbalance_backend.helper.roleAnnotations.ReadOnlyOrAbove;
import com.cloudkeeper.cloudbalance_backend.logging.Logger;
import com.cloudkeeper.cloudbalance_backend.logging.LoggerFactory;
import com.cloudkeeper.cloudbalance_backend.service.AccountAssignmentService;
import com.cloudkeeper.cloudbalance_backend.service.AwsAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AwsAccountController {
    private final AwsAccountService awsAccountService;
    private final AccountAssignmentService assignmentService;
    private final Logger logger = LoggerFactory.getLogger(AwsAccountController.class);

    // ADMIN : Create new AWS account
    @PostMapping
    @AdminOnly
    @Operation(summary = "Create AWS account", description = "Admin creates a new AWS account.")
    public ResponseEntity<ApiResponse<AwsAccountResponse>> createAwsAccount(@Valid @RequestBody AwsAccountCreateRequest request) {
        logger.info("Creating AWS account : {}", request.getAccountAlias());
        AwsAccountResponse response = awsAccountService.createAwsAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<AwsAccountResponse>builder().success(true).message("Account created successfully.").data(response).build());
    }

    // ADMIN: List all AWS accounts
    @GetMapping
    @ReadOnlyOrAbove
    @Operation(summary = "Get all aws accounts", description = "Admin can see all the aws accounts.")
    public ResponseEntity<ApiResponse<List<AwsAccountResponse>>> getAllAwsAccounts() {
        List<AwsAccountResponse> accounts = awsAccountService.getAllAwsAccounts();
        return ResponseEntity.ok(ApiResponse.<List<AwsAccountResponse>>builder().success(true).message("Accounts retrieved successfully.").data(accounts).build());
    }

    // ADMIN: Assign account to customer
    @PostMapping("/assign")
    @AdminOnly
    @Operation(summary = "Assign AWS account", description = "Admin can assign any AWS account to any customer.")
    public ResponseEntity<ApiResponse<Void>> assignAccount(@Valid @RequestBody AccountAssignmentRequest request) {
        logger.info("Assigning account {} to user {}", request.getAwsAccountId(), request.getUserId());
        assignmentService.assignAccount(request.getAwsAccountId(), request.getUserId());
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true).message("Account assigned successfully.").build());
    }

    // ADMIN: Assign account to customer
    @PostMapping("/unassign")
    @AdminOnly
    @Operation(summary = "Unassign AWS account", description = "Admin can unassign any account from the customer.")
    public ResponseEntity<ApiResponse<Void>> unassignAccount(@Valid @RequestBody AccountAssignmentRequest request) {
        logger.info("Unassigning account {} from user {}", request.getAwsAccountId(), request.getUserId());
        assignmentService.unassignAccount(request.getAwsAccountId(), request.getUserId());
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true).message("Account unassigned successfully.").build());
    }

    // Customer: Get my assigned accounts
    @GetMapping("/my-accounts")
    @CustomerOnly
    @Operation(summary = "Get all the assigned accounts", description = "User can get all assigned accounts.")
    public ResponseEntity<ApiResponse<List<AwsAccount>>> getMyAccounts(Authentication auth) {
        logger.info("Fetching accounts for authenticated user.");
        // extract userId from authentication
        Long userId = extractUserId(auth);
        logger.debug("User Id extracted : {}", userId);

        List<AwsAccount> accounts = awsAccountService.getAccountsForUser(userId);

        return ResponseEntity.ok(ApiResponse.<List<AwsAccount>>builder().success(true).message("Your accounts retrieved successfully.").data(accounts).build());
    }

    @DeleteMapping("/{id}")
    @AdminOnly
    public ResponseEntity<ApiResponse<Void>> deleteAccount(@PathVariable Long id) {
        logger.info("Deleting AWS account : {}", id);
        awsAccountService.deleteAwsAccount(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true).message("Account deleted successfully").build());
    }

    private Long extractUserId(Authentication auth) {
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        if (principal != null) {
            return principal.getId();
        }
        // fallback : get from authentication name
        String username = auth.getName();
        logger.debug("Extracting userId from username : {}", username);

        return awsAccountService.getUserIdByEmail(username);
    }

}
