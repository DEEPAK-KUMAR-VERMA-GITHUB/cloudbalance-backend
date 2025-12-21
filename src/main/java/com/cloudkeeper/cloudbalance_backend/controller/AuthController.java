package com.cloudkeeper.cloudbalance_backend.controller;

import com.cloudkeeper.cloudbalance_backend.dto.request.LoginRequest;
import com.cloudkeeper.cloudbalance_backend.dto.request.TokenRefreshRequest;
import com.cloudkeeper.cloudbalance_backend.dto.response.ApiResponse;
import com.cloudkeeper.cloudbalance_backend.dto.response.AuthResponse;
import com.cloudkeeper.cloudbalance_backend.logging.annotation.Loggable;
import com.cloudkeeper.cloudbalance_backend.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication Management APIs")
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Login user", description = "Authenticate user and return jwt token")
    @Loggable(logArgs = true, logResult = true, logExecutionTime = true)
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpServletRequest) {
        AuthResponse authResponse = authService.login(request, httpServletRequest);

        if (authResponse.getHasActiveSession() != null && authResponse.getHasActiveSession()) {
            return ResponseEntity.ok(ApiResponse.<AuthResponse>builder().success(false).message("User already logged in on another device. Use force login to continue.").data(authResponse).build());
        }

        return ResponseEntity.ok(ApiResponse.<AuthResponse>builder().success(true).message("Login successful").data(authResponse).build());
    }

    @PostMapping("/force-login")
    @Operation(summary = "Force login", description = "Force login by terminating other sessions")
    public ResponseEntity<ApiResponse<AuthResponse>> forceLogin(@Valid @RequestBody LoginRequest request, HttpServletRequest httpServletRequest) {
        AuthResponse authResponse = authService.forceLogin(request, httpServletRequest);
        return ResponseEntity.ok(ApiResponse.<AuthResponse>builder().success(true).message("Force login successful. Previous sessions terminated.").data(authResponse).build());
    }

    @PostMapping("/refresh-token")
    @Operation(summary = "Refresh access token", description = "Get new access token using refresh token")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(@Valid @RequestBody TokenRefreshRequest request, HttpServletRequest httpServletRequest) {
        AuthResponse authResponse = authService.refreshToken(request, httpServletRequest);
        return ResponseEntity.ok(ApiResponse.<AuthResponse>builder().success(true).message("Token refreshed successfully").data(authResponse).build());
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout user", description = "Logout user and invalidate tokens")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestHeader("Authorization") String authHeader, Authentication authentication, HttpServletRequest httpServletRequest) {
        String email = authentication.getName();
        String accessToken = authHeader.split("Bearer")[1];
        authService.logout(email, accessToken, httpServletRequest);

        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true).message("Logout successful").build());
    }
}












