package com.cloudkeeper.cloudbalance_backend.controller;

import com.cloudkeeper.cloudbalance_backend.dto.request.LoginRequest;
import com.cloudkeeper.cloudbalance_backend.dto.response.ApiResponse;
import com.cloudkeeper.cloudbalance_backend.dto.response.AuthResponse;
import com.cloudkeeper.cloudbalance_backend.entity.UserSessionRedis;
import com.cloudkeeper.cloudbalance_backend.logging.Logger;
import com.cloudkeeper.cloudbalance_backend.logging.LoggerFactory;
import com.cloudkeeper.cloudbalance_backend.logging.annotation.Loggable;
import com.cloudkeeper.cloudbalance_backend.service.AuthService;
import com.cloudkeeper.cloudbalance_backend.service.JwtService;
import com.cloudkeeper.cloudbalance_backend.service.SessionManagementService;
import com.cloudkeeper.cloudbalance_backend.service.TokenBlackListService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication Management APIs")
public class AuthController {
    private final AuthService authService;
    private final SessionManagementService sessionManagementService;
    private final JwtService jwtService;
    private final TokenBlackListService tokenBlackListService;
    private final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @PostMapping("/login")
    @Operation(summary = "Login user", description = "Authenticate user and return jwt token")
    @Loggable
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        AuthResponse authResponse = authService.login(request, httpServletRequest, httpServletResponse);

        if (authResponse.getHasActiveSession() != null && authResponse.getHasActiveSession()) {
            return ResponseEntity.ok(ApiResponse.<AuthResponse>builder().success(false).message("User already logged in on another device. Use force login to continue.").data(authResponse).build());
        }

        return ResponseEntity.ok(ApiResponse.<AuthResponse>builder().success(true).message("Login successful").data(authResponse).build());
    }

    @PostMapping("/force-login")
    @Operation(summary = "Force login", description = "Force login by terminating other sessions")
    public ResponseEntity<ApiResponse<AuthResponse>> forceLogin(@Valid @RequestBody LoginRequest request, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        AuthResponse authResponse = authService.forceLogin(request, httpServletRequest, httpServletResponse);
        return ResponseEntity.ok(ApiResponse.<AuthResponse>builder().success(true).message("Force login successful. All Previous sessions terminated.").data(authResponse).build());
    }

    @PostMapping("/refresh-token")
    @Operation(summary = "Refresh access token", description = "Get new access token using refresh token")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        AuthResponse authResponse = authService.refreshToken(httpServletRequest, httpServletResponse);
        return ResponseEntity.ok(ApiResponse.<AuthResponse>builder().success(true).message("Token refreshed successfully").data(authResponse).build());
    }


    // get all active sessions for current user
    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<List<UserSessionRedis>>> getActiveSessions(Authentication authentication) {
        List<UserSessionRedis> sessions = authService.getActiveUserSessions(authentication);
        return ResponseEntity.ok(ApiResponse.<List<UserSessionRedis>>builder()
                .success(true)
                .data(sessions)
                .build());
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout user", description = "Logout user and invalidate tokens")
    public ResponseEntity<ApiResponse<Void>> logout(
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        authService.logoutDevice(authentication, request, response);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Logout successful")
                        .build()
        );
    }
}












