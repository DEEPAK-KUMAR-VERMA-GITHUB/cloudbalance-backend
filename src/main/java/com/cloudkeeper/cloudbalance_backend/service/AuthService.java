package com.cloudkeeper.cloudbalance_backend.service;

import com.cloudkeeper.cloudbalance_backend.dto.request.LoginRequest;
import com.cloudkeeper.cloudbalance_backend.dto.response.AuthResponse;
import com.cloudkeeper.cloudbalance_backend.entity.RefreshToken;
import com.cloudkeeper.cloudbalance_backend.entity.User;
import com.cloudkeeper.cloudbalance_backend.entity.UserSessionRedis;
import com.cloudkeeper.cloudbalance_backend.exception.InvalidCredentialsException;
import com.cloudkeeper.cloudbalance_backend.exception.MaxSessionsReachedException;
import com.cloudkeeper.cloudbalance_backend.exception.ResourceNotFoundException;
import com.cloudkeeper.cloudbalance_backend.logging.Logger;
import com.cloudkeeper.cloudbalance_backend.logging.LoggerFactory;
import com.cloudkeeper.cloudbalance_backend.repository.jpa.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;

import java.time.LocalDateTime;
import java.util.List;

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
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        try {
            logger.info("Login attempt for email :{}", request.getEmail());

            // try to authenticate user
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
            logger.info("Authentication successful for: {}", request.getEmail());
            // load user details
            UserDetails userDetails = appUserDetailsService.loadUserByUsername(request.getEmail());
            User user = userRepository.findByEmail(request.getEmail()).orElseThrow(() -> new InvalidCredentialsException("User not found"));

            // extract device info and ip
            String deviceInfo = extractDeviceInfo(httpServletRequest);
            String ipAddress = extractIpAddress(httpServletRequest);
            UserSessionRedis session = null;
            // check max sessions
            try {
                session = sessionManagementService.createSession(user, deviceInfo, ipAddress);
            } catch (MaxSessionsReachedException e) {
                List<UserSessionRedis> activeSessions = sessionManagementService.getActiveUserSessions(user.getId());

                return AuthResponse.builder()
                        .hasActiveSession(true)
                        .message(e.getMessage())
                        .activeSessions(activeSessions)
                        .email(user.getEmail())
                        .build();
            }

            // generate tokens
            String accessToken = jwtService.generateAccessToken(userDetails, user.getId(), user.getTokenVersion(), session.getSessionId());
            RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getId(), deviceInfo, ipAddress, session.getSessionId());

            // initialize token version in redis if not exists
            if (tokenBlackListService.getUserTokenVersion(user.getId()) == null) {
                tokenBlackListService.setUserTokenVersion(user.getId(), user.getTokenVersion());
            }

            logger.info("=== SETTING COOKIES ===");
            logger.info("Access Token: {}...", accessToken.substring(0, 20));
            logger.info("Refresh Token: {}", refreshToken.getToken());

            // set access token in httpOnly cookie
            setAccessTokenCookie(httpServletResponse, accessToken);

            // set refresh token in httpOnly cookie
            setRefreshTokenCookie(httpServletResponse, refreshToken.getToken());

            // update last login
            user.setLastLogin(LocalDateTime.now());
            userRepository.save(user);

            logger.info("User logged in successfully : {}", request.getEmail());

            // Get primary role
            String primaryRole = user.getRole().getDisplayName();

            return AuthResponse.builder()
                    .accessToken(accessToken)
                    .sessionId(session.getSessionId())
                    .type("Bearer")
                    .name(user.getFirstName() + " " + user.getLastName())
                    .email(user.getEmail())
                    .role(primaryRole)
                    .expiresIn(jwtService.getAccessTokenExpiration() / 1000)
                    .hasActiveSession(false)
                    .build();

        } catch (BadCredentialsException | InvalidCredentialsException e) {
            logger.error("Invalid credentials for email: {}", request.getEmail());
            throw new InvalidCredentialsException("Invalid email or password");
        } catch (Exception e) {
            logger.error("Unexpected error during login for email: {}", request.getEmail(), e);
            throw new RuntimeException("Login failed due to unexpected error", e);
        }
    }

    @Transactional
    public AuthResponse forceLogin(@Valid @RequestBody LoginRequest request, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        User user = userRepository.findByEmail(request.getEmail()).orElseThrow(() ->
                new InvalidCredentialsException("User not found")
        );

        // revoke all existing sessions and tokens
        refreshTokenService.revokeAllUserTokens(user);
        sessionManagementService.invalidateAllUserSessions(user.getId());
        tokenBlackListService.incrementUserTokenVersion(user.getId());

        logger.info("Force logout completed for user: {}", user.getEmail());

        return login(request, httpServletRequest, httpServletResponse);
    }

    @Transactional
    public AuthResponse refreshToken(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        // extract refresh token from cookie
        String requestRefreshToken = extractRefreshTokenFromCookie(httpServletRequest);

        if (requestRefreshToken == null) throw new InvalidCredentialsException("Refresh token not found.");

        // validate refresh token
        RefreshToken refreshToken = refreshTokenService.findByToken(requestRefreshToken);
        refreshToken = refreshTokenService.verifyRefreshExpiration(refreshToken);

        User user = refreshToken.getUser();
        UserDetails userDetails = appUserDetailsService.loadUserByUsername(user.getEmail());

        // Extract sessionId from the old access token (even if expired)
        String oldAccessToken = extractAccessToken(httpServletRequest);
        String sessionId = null;

        if (oldAccessToken != null) {
            try {
                sessionId = jwtService.extractSessionId(oldAccessToken);
            } catch (Exception e) {
                logger.warn("Could not extract sessionId from expired token, will retrieve from Redis");
            }
        }

        // If we couldn't get sessionId from token, find active session for user in Redis
        if (sessionId == null) {
            List<UserSessionRedis> activeSessions = sessionManagementService.getActiveUserSessions(user.getId());
            if (activeSessions.isEmpty()) {
                throw new InvalidCredentialsException("No active session found. Please login again.");
            }
            // Use the first active session (or implement logic to choose the correct one)
            sessionId = activeSessions.getFirst().getSessionId();
        }

        // Validate session exists and is active in Redis
        if (!sessionManagementService.isSessionValid(sessionId)) {
            throw new InvalidCredentialsException("Session expired. Please login again.");
        }

        // Update session activity in Redis
        sessionManagementService.updateSessionActivity(sessionId, user.getId());

        // generate new access token
        String accessToken = jwtService.generateAccessToken(userDetails, user.getId(), user.getTokenVersion(), sessionId);

        // set new access token in cookie
        setAccessTokenCookie(httpServletResponse, accessToken);

        logger.info("Token refreshed for user: {}", user.getEmail());

        String primaryRole = user.getRole().getDisplayName();

        return AuthResponse.builder()
                .accessToken(accessToken)
                .type("Bearer")
                .name(user.getFirstName() + " " + user.getLastName())
                .email(user.getEmail())
                .role(primaryRole)
                .expiresIn(jwtService.getAccessTokenExpiration() / 1000)
                .hasActiveSession(false)
                .build();
    }

    @Transactional
    public void logoutDevice(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow(() -> new InvalidCredentialsException("User not found"));
        // extract access token
        String accessToken = extractAccessToken(request);

        if (accessToken != null) {
            tokenBlackListService.blacklistToken(accessToken, jwtService.getAccessTokenExpiration());
        }

        // get session id from request
        String sessionId = request.getSession(false) != null ? request.getSession(false).getId() : null;

        if (sessionId != null) {
            sessionManagementService.invalidateSession(sessionId, user.getId());
            refreshTokenService.revokeRefreshTokenByDevice(user.getId(), sessionId);
            logger.info("User logged out from device: {} session: {}", authentication.getName(), sessionId);
        } else {
            logger.warn("No session found for logout: {}", authentication.getName());
        }


    }

    @Transactional
    public void logout(String email, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new InvalidCredentialsException("User not found"));

        // extract access token
        String accessToken = extractAccessToken(httpServletRequest);

        if (accessToken != null) {
            // Blacklist current access token
            tokenBlackListService.blacklistToken(accessToken, jwtService.getAccessTokenExpiration());

            // revoke all refresh tokens
            refreshTokenService.revokeAllUserTokens(user);
            // invalidate session
            String sessionId = httpServletRequest.getSession(false) != null ? httpServletRequest.getSession(false).getId() : null;

            if (sessionId != null) {
                sessionManagementService.invalidateSession(sessionId, user.getId());
            }

            // clear cookies
            clearAuthCookies(httpServletResponse);

            logger.info("User logged out : {}", email);
        }
    }

    @Transactional(readOnly = true)
    public List<UserSessionRedis> getActiveUserSessions(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return sessionManagementService.getActiveUserSessions(user.getId());
    }

    private void setRefreshTokenCookie(HttpServletResponse httpServletResponse, String token) {
        Cookie cookie = new Cookie("refresh_token", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // TODO: set to true in production
        cookie.setPath("/api/v1/auth");
        cookie.setMaxAge((int) (refreshTokenExpiration / 1000));

        httpServletResponse.addCookie(cookie);
    }

    private void setAccessTokenCookie(HttpServletResponse httpServletResponse, String accessToken) {
        Cookie cookie = new Cookie("access_token", accessToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // TODO: set to true in production
        cookie.setPath("/api");
        cookie.setMaxAge((int) (accessTokenExpiration / 1000));

        httpServletResponse.addCookie(cookie);
    }

    private String extractAccessToken(HttpServletRequest httpServletRequest) {
        // try header first (for mobile apps)
        String authHeader = httpServletRequest.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        // try cookie (for web browsers)
        if (httpServletRequest.getCookies() != null) {
            for (Cookie cookie : httpServletRequest.getCookies()) {
                if ("access_token".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private String extractRefreshTokenFromCookie(HttpServletRequest httpServletRequest) {
        if (httpServletRequest.getCookies() != null) {
            for (Cookie cookie : httpServletRequest.getCookies()) {
                System.out.println("Cookie : - " + cookie.getName());
                if ("refresh_token".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private void clearAuthCookies(HttpServletResponse httpServletResponse) {
        Cookie accessCookie = new Cookie("access_token", null);
        accessCookie.setHttpOnly(true);
        accessCookie.setSecure(false); // TODO: set to true in production
        accessCookie.setPath("/api");
        accessCookie.setMaxAge(0);

        Cookie refreshCookie = new Cookie("refresh_token", null);
        refreshCookie.setHttpOnly(true);
        refreshCookie.setSecure(false); // TODO: set to true in production
        refreshCookie.setPath("/api/v1/auth");
        refreshCookie.setMaxAge(0);

        httpServletResponse.addCookie(accessCookie);
        httpServletResponse.addCookie(refreshCookie);
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
