package com.cloudkeeper.cloudbalance_backend.controller;

import com.cloudkeeper.cloudbalance_backend.dto.request.BulkAccountAssignmentToUserRequest;
import com.cloudkeeper.cloudbalance_backend.dto.request.UserCreateRequest;
import com.cloudkeeper.cloudbalance_backend.dto.request.UserUpdateRequest;
import com.cloudkeeper.cloudbalance_backend.dto.response.ApiResponse;
import com.cloudkeeper.cloudbalance_backend.dto.response.BulkAccountAssignmentResponse;
import com.cloudkeeper.cloudbalance_backend.dto.response.PagedResponse;
import com.cloudkeeper.cloudbalance_backend.dto.response.UserResponse;
import com.cloudkeeper.cloudbalance_backend.entity.AwsAccount;
import com.cloudkeeper.cloudbalance_backend.entity.UserRole;
import com.cloudkeeper.cloudbalance_backend.helper.roleAnnotations.AdminOnly;
import com.cloudkeeper.cloudbalance_backend.helper.roleAnnotations.AnyAuthenticatedUser;
import com.cloudkeeper.cloudbalance_backend.helper.roleAnnotations.ReadOnlyOrAbove;
import com.cloudkeeper.cloudbalance_backend.logging.Logger;
import com.cloudkeeper.cloudbalance_backend.logging.LoggerFactory;
import com.cloudkeeper.cloudbalance_backend.logging.annotation.Loggable;
import com.cloudkeeper.cloudbalance_backend.service.AwsAccountService;
import com.cloudkeeper.cloudbalance_backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "User Management", description = "User management APIs")
public class UserController {

    private final UserService userService;
    private final AwsAccountService awsAccountService;
    private final Logger logger = LoggerFactory.getLogger(UserController.class);

    @GetMapping
    @ReadOnlyOrAbove
    @Operation(summary = "List users with filters (ADMIN only)", description = "Returns paginated list of users with optional filters for search, role, and active status.", parameters = {@Parameter(name = "page", in = ParameterIn.QUERY, description = "Page number (0-based)", example = "0"), @Parameter(name = "size", in = ParameterIn.QUERY, description = "Page size", example = "10"), @Parameter(name = "sortBy", in = ParameterIn.QUERY, description = "Sort field", example = "createdAt"), @Parameter(name = "sortDir", in = ParameterIn.QUERY, description = "Sort direction (asc/desc)", example = "desc"), @Parameter(name = "search", in = ParameterIn.QUERY, description = "Search by name or email", example = "deepak"), @Parameter(name = "active", in = ParameterIn.QUERY, description = "Filter by active status", example = "true"), @Parameter(name = "role", in = ParameterIn.QUERY, description = "Filter by role", example = "ADMIN")})
    public ResponseEntity<ApiResponse<PagedResponse<UserResponse>>> getAllUsers(@RequestParam(defaultValue = "0") Integer page, @RequestParam(defaultValue = "10") Integer size, @RequestParam(defaultValue = "createdAt") String sortBy, @RequestParam(defaultValue = "desc") String sortDir, @RequestParam(required = false) String search, @RequestParam(required = false) Boolean active, @RequestParam(required = false) UserRole role) {
        PagedResponse<UserResponse> users = userService.getAllUsers(page, size, sortBy, sortDir, search, active, role);
        return ResponseEntity.ok(ApiResponse.<PagedResponse<UserResponse>>builder().success(true).message("Users retrieved successfully").data(users).build());
    }

    @GetMapping("/{id}")
    @ReadOnlyOrAbove
    @Operation(summary = "Get user by ID (ADMIN only) ", description = "Retrieve a specific user by his/her ID. Only ADMIN can call this endpoint.")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable("id") Long userId) {
        UserResponse user = userService.getUserById(userId);
        return ResponseEntity.ok(ApiResponse.<UserResponse>builder().success(true).message("User retrieved successfully").data(user).build());
    }

    @PostMapping
    @AdminOnly
    @Operation(summary = "Create new user (ADMIN only) ", description = "Create a new user with given roles. Only ADMIN can call this endpoint.", responses = {@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "User created successfully", content = @Content(schema = @Schema(implementation = ApiResponse.class))), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "User with given email already exists")})
    public ResponseEntity<ApiResponse<UserResponse>> createUser(@Valid @RequestBody UserCreateRequest request) {

        UserResponse user = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<UserResponse>builder().success(true).message("User created successfully").data(user).build());
    }

    @PutMapping("/{id}")
    @AdminOnly
    @Operation(summary = "Update an user (ADMIN only) ", description = "Update basic user info and roles. Only ADMIN can call this endpoint.")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(@PathVariable("id") Long userid, @Valid @RequestBody UserUpdateRequest request) {
        UserResponse user = userService.updateUser(userid, request);
        return ResponseEntity.ok(ApiResponse.<UserResponse>builder().success(true).message("User updated successfully").data(user).build());
    }

    @PatchMapping("/{id}/activate")
    @AdminOnly
    @Operation(summary = "Activate user (ADMIN only)", description = "Activates a user account. Only ADMIN can call this endpoint.")
    public ResponseEntity<ApiResponse<UserResponse>> activateUser(@PathVariable("id") Long userId) {
        UserResponse user = userService.activateUser(userId);
        return ResponseEntity.ok(ApiResponse.<UserResponse>builder().success(true).message("User activated successfully").data(user).build());
    }

    @PatchMapping("/{id}/deactivate")
    @AdminOnly
    @Operation(summary = "Deactivate user (ADMIN only)", description = "Deactivates a user account. Only ADMIN can call this endpoint.")
    public ResponseEntity<ApiResponse<UserResponse>> deactivateUser(@PathVariable("id") Long userId) {
        UserResponse user = userService.deactivateUser(userId);
        return ResponseEntity.ok(ApiResponse.<UserResponse>builder().success(true).message("User deactivated successfully").data(user).build());
    }

    @DeleteMapping("/{id}")
    @AdminOnly
    @Operation(summary = "Delete an user (ADMIN only)", description = "Soft delete : marks user inactive and revokes tokens/sessions.")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable("id") Long userId) {
        userService.deleteUser(userId);
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true).message("User Deleted Successfully").build());
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user", description = "Get authenticated user information")
    @AnyAuthenticatedUser
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(Authentication authentication) {
        logger.info("Fetching current user info.");
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.<UserResponse>builder().success(false).message("Not authenticated").build());
        }

        String email = authentication.getName();
        UserResponse user = userService.getUserByEmail(email);

        return ResponseEntity.ok(ApiResponse.<UserResponse>builder().success(true).message("Current user retrieved successfully.").data(user).build());

    }

    /**
     * Assign multiple AWS accounts to a single user
     */
    @PostMapping("/{userId}/accounts/assign-bulk")
    @AdminOnly
    @Operation(summary = "Assign multiple AWS accounts to a user", description = "Admin assigns multiple AWS accounts to a specific user in bulk operation")
    public ResponseEntity<ApiResponse<BulkAccountAssignmentResponse>> assignMultipleAccountsToUser(@PathVariable Long userId, @Valid @RequestBody List<Long> accountIds) {

        logger.info("Bulk assignment request: {} accounts to user {}", accountIds.size(), userId);

        BulkAccountAssignmentToUserRequest request = BulkAccountAssignmentToUserRequest.builder().userId(userId).accountIds(accountIds).build();

        BulkAccountAssignmentResponse response = awsAccountService.assignMultipleAccountsToUser(request);

        return ResponseEntity.ok(ApiResponse.<BulkAccountAssignmentResponse>builder().success(true).message(String.format("Bulk assignment completed. Success: %d, Skipped: %d, Failed: %d", response.getSuccessfulAssignments(), response.getSkippedDuplicates(), response.getFailedAssignments())).data(response).build());
    }

    /**
     * Remove multiple AWS accounts from a user
     */
    @PostMapping("/{userId}/accounts/remove-bulk")
    @AdminOnly
    @Operation(summary = "Remove multiple AWS accounts from a user", description = "Admin removes multiple AWS accounts from a user in bulk operation")
    public ResponseEntity<ApiResponse<BulkAccountAssignmentResponse>> removeMultipleAccountsFromUser(@PathVariable Long userId, @Valid @RequestBody List<Long> accountIds) {

        logger.info("Bulk removal request: {} accounts from user {}", accountIds.size(), userId);

        BulkAccountAssignmentToUserRequest request = BulkAccountAssignmentToUserRequest.builder().userId(userId).accountIds(accountIds).build();

        BulkAccountAssignmentResponse response = awsAccountService.removeMultipleAccountsFromUser(request);

        return ResponseEntity.ok(ApiResponse.<BulkAccountAssignmentResponse>builder().success(true).message(String.format("Bulk removal completed. Success: %d, Skipped: %d, Failed: %d", response.getSuccessfulAssignments(), response.getSkippedDuplicates(), response.getFailedAssignments())).data(response).build());
    }

    /**
     * Get all accounts assigned to a user
     */
    @GetMapping("/{userId}/accounts")
    @AdminOnly
    @Operation(summary = "Get user's assigned accounts", description = "Retrieve all AWS accounts assigned to a specific user")
    public ResponseEntity<ApiResponse<List<AwsAccount>>> getUserAssignedAccounts(@PathVariable Long userId) {

        logger.info("Fetching assigned accounts for user {}", userId);

        List<AwsAccount> accounts = awsAccountService.getUserAssignedAccounts(userId);

        return ResponseEntity.ok(ApiResponse.<List<AwsAccount>>builder().success(true).message(String.format("Found %d assigned accounts", accounts.size())).data(accounts).build());
    }


}














