package com.cloudkeeper.cloudbalance_backend.controller;

import com.cloudkeeper.cloudbalance_backend.dto.request.LoginRequest;
import com.cloudkeeper.cloudbalance_backend.dto.request.TokenRefreshRequest;
import com.cloudkeeper.cloudbalance_backend.dto.response.ApiResponse;
import com.cloudkeeper.cloudbalance_backend.dto.response.AuthResponse;
import com.cloudkeeper.cloudbalance_backend.logging.Logger;
import com.cloudkeeper.cloudbalance_backend.logging.LoggerFactory;
import com.cloudkeeper.cloudbalance_backend.logging.annotation.Loggable;
import com.cloudkeeper.cloudbalance_backend.service.AuthService;
import com.cloudkeeper.cloudbalance_backend.service.JwtService;
import com.cloudkeeper.cloudbalance_backend.service.TokenBlackListService;
import io.jsonwebtoken.ExpiredJwtException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication Management APIs")
public class AuthController {
    private final AuthService authService;
    private final JwtService jwtService;
    private final TokenBlackListService tokenBlackListService;
    private final Logger logger = LoggerFactory.getLogger(AuthController.class);

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
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest request,
            @RequestHeader(value = "Authorization", required = true) String authHeader) {

        try {
            // validate bearer format
            if (!authHeader.startsWith("Bearer ")) {
                logger.warn("Invalid Authorization header format");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(
                                ApiResponse.<Void>builder()
                                        .success(false)
                                        .message("Invalid authorization header format")
                                        .build()
                        );
            }

            // Extract JWT
            String accessToken = authHeader.split("Bearer ")[1];

            // check if token is already blacklisted
            if (tokenBlackListService.isTokenBlacklisted(accessToken)) {
                logger.warn("Attempted logout with already blacklisted token");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.<Void>builder()
                                .success(false)
                                .message("Token already invalidated.")
                                .build()
                        );
            }

            // extract user from token
            String userEmail = jwtService.extractUsername(accessToken);
            Long userId = jwtService.extractUserId(accessToken);

            if (userEmail == null || userId == null) {
                logger.error("Failed to extract user from token");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                        ApiResponse.<Void>builder()
                                .success(false)
                                .message("Invalid token")
                                .build()
                );
            }

            // perform logout
            authService.logout(userEmail, accessToken, request);
            logger.info("✅ User logged out successfully: {}", userEmail);

            return ResponseEntity.ok(
                    ApiResponse.<Void>builder()
                            .success(true)
                            .message("Logout successful")
                            .build()
            );
        } catch (ExpiredJwtException e) {
            logger.warn("⚠️ Expired token used for logout");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.<Void>builder()
                            .success(false)
                            .message("Token expired")
                            .build());
        } catch (Exception e) {
            logger.error("💥 Logout error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.<Void>builder()
                            .success(false)
                            .message("Logout failed")
                            .build());
        }
    }
}












