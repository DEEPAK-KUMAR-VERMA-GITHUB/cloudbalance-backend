package com.cloudkeeper.cloudbalance_backend.service;

import com.cloudkeeper.cloudbalance_backend.dto.request.LoginRequest;
import com.cloudkeeper.cloudbalance_backend.dto.request.TokenRefreshRequest;
import com.cloudkeeper.cloudbalance_backend.dto.response.AuthResponse;
import com.cloudkeeper.cloudbalance_backend.entity.RefreshToken;
import com.cloudkeeper.cloudbalance_backend.entity.User;
import com.cloudkeeper.cloudbalance_backend.entity.UserSession;
import com.cloudkeeper.cloudbalance_backend.exception.InvalidCredentialsException;
import com.cloudkeeper.cloudbalance_backend.logging.Logger;
import com.cloudkeeper.cloudbalance_backend.logging.LoggerFactory;
import com.cloudkeeper.cloudbalance_backend.logging.annotation.Loggable;
import com.cloudkeeper.cloudbalance_backend.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final AppUserDetailsService appUserDetailsService;
    private final RefreshTokenService refreshTokenService;
    private final SessionManagementService sessionManagementService;
    private final TokenBlackListService tokenBlackListService;

    @Transactional
    @Loggable(logArgs = false, logResult = true)
    public AuthResponse login(LoginRequest request, HttpServletRequest httpServletRequest) {
        try {
            // try to authenticate user
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
            logger.info("Authentication successful for: {}", request.getEmail());
            // load user details
            UserDetails userDetails = appUserDetailsService.loadUserByUsername(request.getEmail());
            // update last login
            User user = userRepository.findByEmail(request.getEmail()).orElseThrow(() -> new InvalidCredentialsException("User not found"));

            // check if user has active session (single device login)
            boolean hasActiveSession = refreshTokenService.hasActiveSession(user);

            if (hasActiveSession) {
                // return response indicating active session exists
                return AuthResponse.builder()
                        .hasActiveSession(true)
                        .email(user.getEmail())
                        .name(user.getFirstName() + " " + user.getLastName())
                        .build();
            }

            // extract device info and ip
            String deviceInfo = extractDeviceInfo(httpServletRequest);
            String ipAddress = extractIpAddress(httpServletRequest);

            // initialize token version in redis if not exists
            if (tokenBlackListService.getUserTokenVersion(user.getId()) == null) {
                tokenBlackListService.setUserTokenVersion(user.getId(), user.getTokenVersion());
            }

            // generate tokens
            String accessToken = jwtService.generateAccessToken(userDetails, user.getId(), user.getTokenVersion());

            // create refresh token
            RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getId(), deviceInfo, ipAddress);

            // create session
            UserSession session = sessionManagementService.createSession(user, deviceInfo, ipAddress);

            // update last login
            user.setLastLogin(LocalDateTime.now());
            userRepository.save(user);

            logger.info("User logged in successfully : {}", request.getEmail());

            // Get primary role
            String primaryRole = user.getRoles().isEmpty() ? "USER" : user.getRoles().iterator().next().getDisplayName();

            return AuthResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken.getToken())
                    .type("Bearer")
                    .name(user.getFirstName() + " " + user.getLastName())
                    .email(user.getEmail())
                    .role(primaryRole)
                    .expiresIn(jwtService.getAccessTokenExpiration() / 1000)
                    .hasActiveSession(false)
                    .build();

        } catch (BadCredentialsException e) {
            logger.error("Invalid credentials for email: {}", request.getEmail());
            throw new InvalidCredentialsException("Invalid email or password");
        } catch (InvalidCredentialsException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during login for email: {}", request.getEmail(), e);
            throw new RuntimeException("Login failed due to unexpected error", e);
        }
    }

    @Transactional
    public AuthResponse forceLogin(LoginRequest request, HttpServletRequest httpServletRequest) {
        User user = userRepository.findByEmail(request.getEmail()).orElseThrow(() -> {
            throw new InvalidCredentialsException("User not found");
        });

        // revoke all existing sessions and tokens
        refreshTokenService.revokeAllUserTokens(user);
        sessionManagementService.invalidateAllUserSessions(user);

        logger.info("Force logout completed for user: {}", user.getEmail());

        return login(request, httpServletRequest);
    }

    @Transactional
    public AuthResponse refreshToken(TokenRefreshRequest request, HttpServletRequest httpServletRequest) {
        String requestRefreshToken = request.getRefreshToken();
        RefreshToken refreshToken = refreshTokenService.findByToken(requestRefreshToken);
        refreshToken = refreshTokenService.verifyRefreshExpiration(refreshToken);

        User user = refreshToken.getUser();
        UserDetails userDetails = appUserDetailsService.loadUserByUsername(user.getEmail());

        // check session validity
        String sessionId = httpServletRequest.getSession().getId();
        if (!sessionManagementService.isSessionValid(sessionId)) {
            throw new InvalidCredentialsException("Session expired. Please login again.");
        }

        // update session activity
        sessionManagementService.updateSessionActivity(sessionId);

        // generate new access token
        String accessToken = jwtService.generateAccessToken(userDetails, user.getId(), user.getTokenVersion());

        logger.info("Token refreshed for user: {}", user.getEmail());

        String primaryRole = user.getRoles().isEmpty() ? "USER" : user.getRoles().iterator().next().getDisplayName();

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(requestRefreshToken)
                .type("Bearer")
                .name(user.getFirstName() + " " + user.getLastName())
                .email(user.getEmail())
                .role(primaryRole)
                .expiresIn(jwtService.getAccessTokenExpiration() / 1000)
                .hasActiveSession(false)
                .build();
    }

    @Transactional
    public void logout(String email, String accessToken, HttpServletRequest httpServletRequest) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> {
            throw new InvalidCredentialsException("User not found");
        });

        // Blacklist current access token
        tokenBlackListService.blacklistToken(accessToken, jwtService.getAccessTokenExpiration());

        // revoke all refresh tokens
        refreshTokenService.revokeAllUserTokens(user);
        // invalidate session
        String sessionId = httpServletRequest.getSession().getId();
        sessionManagementService.invalidateSession(sessionId);

        logger.info("User logged out : {}", email);
    }

    private String extractIpAddress(HttpServletRequest request) {
        String xForwaredFor = request.getHeader("X-Forwarded-For");
        if (xForwaredFor != null && !xForwaredFor.isBlank()) {
            return xForwaredFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String extractDeviceInfo(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        return userAgent != null ? userAgent : "Unknown Device";
    }


}
