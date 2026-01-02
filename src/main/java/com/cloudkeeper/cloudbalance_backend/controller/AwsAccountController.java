package com.cloudkeeper.cloudbalance_backend.controller;

import com.cloudkeeper.cloudbalance_backend.config.UserPrincipal;
import com.cloudkeeper.cloudbalance_backend.dto.request.AccountAssignmentRequest;
import com.cloudkeeper.cloudbalance_backend.dto.request.AwsAccountCreateRequest;
import com.cloudkeeper.cloudbalance_backend.dto.response.ApiResponse;
import com.cloudkeeper.cloudbalance_backend.dto.response.AwsAccountResponse;
import com.cloudkeeper.cloudbalance_backend.entity.AwsAccount;
import com.cloudkeeper.cloudbalance_backend.helper.roleAnnotations.AdminOnly;
import com.cloudkeeper.cloudbalance_backend.helper.roleAnnotations.AdminOrCustomer;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
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
        AwsAccountResponse response = awsAccountService.createAwsAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<AwsAccountResponse>builder()
                        .success(true)
                        .message("Account created successfully.")
                        .data(response)
                        .build()
        );
    }

    // ADMIN: List all AWS accounts
    @GetMapping
    @AdminOnly
    public ResponseEntity<ApiResponse<List<AwsAccountResponse>>> getAllAwsAccounts() {
        List<AwsAccountResponse> accounts = awsAccountService.getAllAwsAccounts();
        return ResponseEntity.ok(
                ApiResponse.<List<AwsAccountResponse>>builder()
                        .success(true)
                        .data(accounts)
                        .build()
        );
    }

    // ADMIN: Assign account to customer
    @PostMapping("/assign")
    @AdminOnly
    public ResponseEntity<ApiResponse<Void>> assignAccount(@RequestBody AccountAssignmentRequest request) {
        assignmentService.assignAccount(request.getAwsAccountId(), request.getUserId());
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Account assigned successfully.")
                        .build()
        );
    }

    // ADMIN: Assign account to customer
    @PostMapping("/unassign")
    @AdminOnly
    public ResponseEntity<ApiResponse<Void>> unassignAccount(@RequestBody AccountAssignmentRequest request) {
        assignmentService.unassignAccount(request.getAwsAccountId(), request.getUserId());
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Account unassigned successfully.")
                        .build()
        );
    }

    // Customer: Get my assigned accounts
    @GetMapping("/my-accounts")
    @AdminOrCustomer
    public ResponseEntity<ApiResponse<List<AwsAccount>>> getMyAccounts(Authentication auth) {
        // extract userId from authentication
        Long userId = extractUserId(auth);
        logger.debug("User Id extracted : {}", userId);

        List<AwsAccount> accounts = awsAccountService.getCustomerAccounts(userId);

        return ResponseEntity.ok(
                ApiResponse.<List<AwsAccount>>builder()
                        .success(true)
                        .message("Your accounts retrieved successfully.")
                        .data(accounts)
                        .build()
        );
    }

    @PatchMapping("/{accountId}/activate")
    @AdminOnly
    @Operation(summary = "Activate AWS account", description = "Admin activates an AWS account.")
    public ResponseEntity<ApiResponse<Void>> activateAccount(@PathVariable("accountId") Long accountId) {
        logger.info("Activate AWS account request : {}", accountId);

        awsAccountService.activateAccount(accountId);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Account activated successfully.")
                        .build()
        );
    }

    @PatchMapping("/{accountId}/deactivate")
    @AdminOnly
    @Operation(summary = "Deactivate AWS account", description = "Admin deactivates an AWS account.")
    public ResponseEntity<ApiResponse<Void>> deactivateAccount(@PathVariable("accountId") Long accountId) {
        logger.info("Deactivate AWS account request : {}", accountId);

        awsAccountService.deactivateAccount(accountId);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Account deactivated successfully.")
                        .build()
        );
    }

    private Long extractUserId(Authentication auth) {
        Object principal = auth.getPrincipal();
        if (principal != null) {
            logger.debug("Principal type : {}", principal.getClass().getName());

            // try user principal first from my custom implementation
            if (principal instanceof UserPrincipal) {
                UserPrincipal userPrincipal = (UserPrincipal) principal;
                return userPrincipal.getId();
            }

            // try spring security user details
            if (principal instanceof UserDetails) {
                UserDetails userDetails = (UserDetails) principal;
                String username = userDetails.getUsername();
                // fetch userId from database
                return awsAccountService.getUserIdByEmail(username);
            }

        }
        // fallback : get from authentication name
        String username = auth.getName();
        logger.debug("Extracting userId from username : {}", username);

        return awsAccountService.getUserIdByEmail(username);
    }

}
