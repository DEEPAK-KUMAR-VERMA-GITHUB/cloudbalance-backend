package com.cloudkeeper.cloudbalance_backend.service;

import com.cloudkeeper.cloudbalance_backend.dto.request.LoginRequest;
import com.cloudkeeper.cloudbalance_backend.dto.response.AuthResponse;
import com.cloudkeeper.cloudbalance_backend.entity.RefreshToken;
import com.cloudkeeper.cloudbalance_backend.entity.User;
import com.cloudkeeper.cloudbalance_backend.entity.UserSession;
import com.cloudkeeper.cloudbalance_backend.exception.InvalidCredentialsException;
import com.cloudkeeper.cloudbalance_backend.logging.Logger;
import com.cloudkeeper.cloudbalance_backend.logging.LoggerFactory;
import com.cloudkeeper.cloudbalance_backend.repository.RefreshTokenRepository;
import com.cloudkeeper.cloudbalance_backend.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
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

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        try {
            logger.info("Login attempt for email :{}", request.getEmail());

            // try to authenticate user
            Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
            logger.info("Authentication successful for: {}", request.getEmail());
            // load user details
            UserDetails userDetails = appUserDetailsService.loadUserByUsername(request.getEmail());
            User user = userRepository.findByEmail(request.getEmail()).orElseThrow(() -> new InvalidCredentialsException("User not found"));

            // check if user has active session (single device login)
            boolean hasActiveSession = refreshTokenService.hasActiveSession(user);

            if (hasActiveSession) {
                logger.warn("User already has active session : {}", user.getEmail());
                return AuthResponse.builder()
                        .hasActiveSession(true)
                        .email(user.getEmail())
                        .name(user.getFirstName() + " " + user.getLastName())
                        .build();
            }

            // extract device info and ip
            String deviceInfo = extractDeviceInfo(httpServletRequest);
            String ipAddress = extractIpAddress(httpServletRequest);
            // generate tokens
            String accessToken = jwtService.generateAccessToken(userDetails, user.getId(), user.getTokenVersion());
            RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getId(), deviceInfo, ipAddress);

            // initialize token version in redis if not exists
            if (tokenBlackListService.getUserTokenVersion(user.getId()) == null) {
                tokenBlackListService.setUserTokenVersion(user.getId(), user.getTokenVersion());
            }

            // create session
            UserSession session = sessionManagementService.createSession(user, deviceInfo, ipAddress);

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
            String primaryRole = user.getRoles().isEmpty() ? "CUSTOMER" : user.getRoles().iterator().next().getDisplayName();

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
    public AuthResponse forceLogin(LoginRequest request, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        User user = userRepository.findByEmail(request.getEmail()).orElseThrow(() ->
                new InvalidCredentialsException("User not found")
        );

        // revoke all existing sessions and tokens
        refreshTokenService.revokeAllUserTokens(user);
        sessionManagementService.invalidateAllUserSessions(user);

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

        // generate new access token
        String accessToken = jwtService.generateAccessToken(userDetails, user.getId(), user.getTokenVersion());

        // set new access token in cookie
        setAccessTokenCookie(httpServletResponse, accessToken);

        logger.info("Token refreshed for user: {}", user.getEmail());

        String primaryRole = user.getRoles().isEmpty() ? "CUSTOMER" : user.getRoles().iterator().next().getDisplayName();

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
                sessionManagementService.invalidateSession(sessionId);
            }

            // clear cookies
            clearAuthCookies(httpServletResponse);

            logger.info("User logged out : {}", email);
        }
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
