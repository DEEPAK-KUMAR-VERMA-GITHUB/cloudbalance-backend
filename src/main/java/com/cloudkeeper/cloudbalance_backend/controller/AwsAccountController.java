package com.cloudkeeper.cloudbalance_backend.controller;

import com.cloudkeeper.cloudbalance_backend.config.UserPrincipal;
import com.cloudkeeper.cloudbalance_backend.dto.request.AccountAssignmentRequest;
import com.cloudkeeper.cloudbalance_backend.dto.request.AwsAccountCreateRequest;
import com.cloudkeeper.cloudbalance_backend.dto.response.ApiResponse;
import com.cloudkeeper.cloudbalance_backend.dto.response.AwsAccountResponse;
import com.cloudkeeper.cloudbalance_backend.entity.AwsAccount;
import com.cloudkeeper.cloudbalance_backend.logging.annotation.Loggable;
import com.cloudkeeper.cloudbalance_backend.service.AccountAssignmentService;
import com.cloudkeeper.cloudbalance_backend.service.AwsAccountService;
import io.jsonwebtoken.Jwt;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AwsAccountController {
    private final AwsAccountService awsAccountService;
    private final AccountAssignmentService assignmentService;

    // ADMIN : Create new AWS account
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
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
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<AwsAccountResponse>>> getAllAwsAccounts() {
        return ResponseEntity.ok(
                ApiResponse.<List<AwsAccountResponse>>builder()
                        .success(true)
                        .data(awsAccountService.getAllAwsAccounts())
                        .build()
        );
    }

    // ADMIN: Assign account to customer
    @PostMapping("/assign")
    @PreAuthorize("hasRole('ADMIN')")
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
    @PreAuthorize("hasRole('ADMIN')")
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
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<List<AwsAccount>>> getMyAccounts(Authentication auth) {

        System.out.println("=== FULL PRINCIPAL DEBUG ===");
        Object principal = auth.getPrincipal();

        System.out.println("1. Principal TYPE: " + principal.getClass().getName());
        System.out.println("2. Principal TOSTRING: " + principal);
        System.out.println("3. Principal methods:");

        // List ALL methods available
        java.lang.reflect.Method[] methods = principal.getClass().getMethods();
        for (java.lang.reflect.Method method : methods) {
            if (method.getName().startsWith("get") || method.getName().equals("getId")) {
                System.out.println("   → " + method.getName());
            }
        }

        // Try to get username (always works)
        String username = auth.getName();
        System.out.println("4. Username from auth.getName(): " + username);

        System.out.println("=== END DEBUG ===");

        return ResponseEntity.ok().build();
    }

}
