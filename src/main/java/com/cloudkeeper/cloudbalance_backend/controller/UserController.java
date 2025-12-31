package com.cloudkeeper.cloudbalance_backend.controller;

import com.cloudkeeper.cloudbalance_backend.dto.request.UserCreateRequest;
import com.cloudkeeper.cloudbalance_backend.dto.request.UserUpdateRequest;
import com.cloudkeeper.cloudbalance_backend.dto.response.ApiResponse;
import com.cloudkeeper.cloudbalance_backend.dto.response.PagedResponse;
import com.cloudkeeper.cloudbalance_backend.dto.response.UserResponse;
import com.cloudkeeper.cloudbalance_backend.entity.UserRole;
import com.cloudkeeper.cloudbalance_backend.logging.annotation.Loggable;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "User Management", description = "User management APIs")
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "List users with filters (ADMIN only)",
            description = "Returns paginated list of users with optional filters for search, role, and active status.",
            parameters = {
                    @Parameter(
                            name = "page", in = ParameterIn.QUERY,
                            description = "Page number (0-based)",
                            example = "0"
                    ),
                    @Parameter(name = "size", in = ParameterIn.QUERY, description = "Page size", example = "10"),
                    @Parameter(name = "sortBy", in = ParameterIn.QUERY, description = "Sort field", example = "createdAt"),
                    @Parameter(name = "sortDir", in = ParameterIn.QUERY, description = "Sort direction (asc/desc)", example = "desc"),
                    @Parameter(name = "search", in = ParameterIn.QUERY, description = "Search by name or email", example = "deepak"),
                    @Parameter(name = "active", in = ParameterIn.QUERY, description = "Filter by active status", example = "true"),
                    @Parameter(name = "role", in = ParameterIn.QUERY, description = "Filter by role", example = "ADMIN")
            }
    )
    public ResponseEntity<ApiResponse<PagedResponse<UserResponse>>> getAllUsers(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) UserRole role
    ) {
        PagedResponse<UserResponse> users = userService.getAllUsers(
                page, size, sortBy, sortDir, search, active, role
        );
        return ResponseEntity.ok(
                ApiResponse.<PagedResponse<UserResponse>>builder()
                        .success(true)
                        .message("Users retrieved successfully")
                        .data(users)
                        .build()
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get user by ID (ADMIN only) ", description = "Retrieve a specific user by his/her ID. Only ADMIN can call this endpoint.")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable("id") Long userId) {
        UserResponse user = userService.getUserById(userId);
        return ResponseEntity.ok(ApiResponse.<UserResponse>builder()
                .success(true)
                .message("User retrieved successfully")
                .data(user)
                .build());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Create new user (ADMIN only) ",
            description = "Create a new user with given roles. Only ADMIN can call this endpoint.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "201",
                            description = "User created successfully",
                            content = @Content(schema = @Schema(implementation = ApiResponse.class))
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "400",
                            description = "Validation error"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "409",
                            description = "User with given email already exists"
                    )
            }
    )
    public ResponseEntity<ApiResponse<UserResponse>> createUser(@Valid @RequestBody UserCreateRequest request) {

        UserResponse user = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<UserResponse>builder()
                        .success(true)
                        .message("User created successfully")
                        .data(user)
                        .build()
        );
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Update an user (ADMIN only) ",
            description = "Update basic user info and roles. Only ADMIN can call this endpoint."
    )
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(@PathVariable("id") Long userid, @Valid @RequestBody UserUpdateRequest request) {
        UserResponse user = userService.updateUser(userid, request);
        return ResponseEntity.ok(
                ApiResponse.<UserResponse>builder()
                        .success(true)
                        .message("User updated successfully")
                        .data(user)
                        .build()
        );
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Activate user (ADMIN only)",
            description = "Activates a user account. Only ADMIN can call this endpoint."
    )
    public ResponseEntity<ApiResponse<UserResponse>> activateUser(@PathVariable("id") Long userId) {
        UserResponse user = userService.activateUser(userId);
        return ResponseEntity.ok(
                ApiResponse.<UserResponse>builder()
                        .success(true)
                        .message("User activated successfully")
                        .data(user)
                        .build()
        );
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Deactivate user (ADMIN only)",
            description = "Deactivates a user account. Only ADMIN can call this endpoint."
    )
    public ResponseEntity<ApiResponse<UserResponse>> deactivateUser(@PathVariable("id") Long userId) {
        UserResponse user = userService.deactivateUser(userId);
        return ResponseEntity.ok(
                ApiResponse.<UserResponse>builder()
                        .success(true)
                        .message("User deactivated successfully")
                        .data(user)
                        .build()
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete an user (ADMIN only)", description = "Soft delete : marks user inactive and revokes tokens/sessions.")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable("id") Long userId) {
        userService.deleteUser(userId);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("User Deleted Successfully")
                        .build()
        );
    }


}














