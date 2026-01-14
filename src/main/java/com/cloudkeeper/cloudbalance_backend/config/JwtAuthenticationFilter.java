package com.cloudkeeper.cloudbalance_backend.config;

import com.cloudkeeper.cloudbalance_backend.dto.response.AuthResponse;
import com.cloudkeeper.cloudbalance_backend.entity.RefreshToken;
import com.cloudkeeper.cloudbalance_backend.entity.User;
import com.cloudkeeper.cloudbalance_backend.entity.UserSessionRedis;
import com.cloudkeeper.cloudbalance_backend.logging.Logger;
import com.cloudkeeper.cloudbalance_backend.logging.LoggerFactory;
import com.cloudkeeper.cloudbalance_backend.repository.jpa.RefreshTokenRepository;
import com.cloudkeeper.cloudbalance_backend.repository.jpa.UserRepository;
import com.cloudkeeper.cloudbalance_backend.service.*;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AppUserDetailsService appUserDetailsService;
    private final TokenBlackListService tokenBlackListService;
    private final SessionManagementService sessionManagementService;
    private final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenService refreshTokenService;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        // Skip filter for auth endpoints
        String requestURI = request.getRequestURI();
        if (requestURI.contains("/auth/login") ||
                requestURI.contains("/auth/register") ||
                requestURI.contains("/auth/force-login")) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = null;

        // Try 1: Get JWT from Authorization header (for mobile/API clients)
        final String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            jwt = authHeader.substring(7);
            logger.debug("JWT extracted from Authorization header");
        }

        // Try 2: Get JWT from HttpOnly cookie (for web browsers)
        if (jwt == null && request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("access_token".equals(cookie.getName())) {
                    jwt = cookie.getValue();
                    logger.debug("JWT extracted from cookie");
                    break;
                }
            }
        }

        if (jwt == null) {
            logger.debug("No JWT found - continuing without authentication");
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // 1. Check if token is blacklisted
            if (tokenBlackListService.isTokenBlacklisted(jwt)) {
                logger.warn("BLACKLISTED token attempted");
                sendErrorResponse(response, "Token has been revoked", HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
            logger.debug("Token NOT blacklisted");

            // 2. Extract user information (this might throw ExpiredJwtException)
            String userEmail = null;
            Long userId = null;
            boolean tokenExpired = false;

            try {
                userEmail = jwtService.extractUsername(jwt);
                userId = jwtService.extractUserId(jwt);
                logger.debug("Extracted - Email: {}, UserId: {}", userEmail, userId);
            } catch (io.jsonwebtoken.ExpiredJwtException e) {
                // Token expired - we'll attempt auto-refresh
                logger.warn("Token expired during extraction - will attempt auto-refresh");
                tokenExpired = true;
                // Extract claims from expired token
                userEmail = e.getClaims().getSubject();
                userId = e.getClaims().get("userId", Long.class);
                logger.debug("Extracted from expired token - Email: {}, UserId: {}", userEmail, userId);
            }

            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = this.appUserDetailsService.loadUserByUsername(userEmail);
                logger.debug("UserDetails loaded: {}", userDetails.getUsername());

                // 3. Get session ID from JWT
                String sessionId = jwtService.extractSessionId(jwt);

                if (sessionId == null) {
                    logger.warn("No sessionId in JWT token");
                    sendErrorResponse(response, "Invalid token", HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }
                logger.debug("Session ID from JWT: {}", sessionId);

                // 4. Check if token is valid or needs refresh
                boolean tokenWasRefreshed = false;
                boolean isValid = false;

                if (!tokenExpired) {
                    // Token not expired yet, validate normally
                    isValid = jwtService.isTokenValid(jwt, userDetails);
                }

                if (tokenExpired || !isValid) {
                    logger.warn("Token expired or invalid - Attempting auto-refresh...");

                    AuthResponse refreshed = performAutoRefresh(sessionId, userId, userDetails, response);

                    if (refreshed != null) {
                        tokenWasRefreshed = true;
                        logger.info("✅ Auto-Refresh successful!");
                    } else {
                        logger.warn("❌ Auto-refresh failed for session: {}", sessionId);
                        sendErrorResponse(response, "Session expired. Please login again.", HttpServletResponse.SC_UNAUTHORIZED);
                        return;
                    }
                } else {
                    logger.debug("Token VALID - No refresh needed");
                }

                // 5. Validate session (idle timeout check)
                if (!sessionManagementService.isSessionValid(sessionId)) {
                    logger.warn("SESSION INVALID (idle timeout): {}", sessionId);
                    sendErrorResponse(response, "Session expired due to inactivity. Please login again.", HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }
                logger.debug("Session VALID");

                // 6. Update session activity (keep alive)
                sessionManagementService.updateSessionActivity(sessionId, userId);

                // 7. Set Spring Security authentication context
                UsernamePasswordAuthenticationToken authenticationToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities()
                        );
                authenticationToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);

                if (tokenWasRefreshed) {
                    logger.info("🔄 AUTO-REFRESH AUTH SET SUCCESSFULLY for user: {}", userEmail);
                } else {
                    logger.debug("✅ AUTHENTICATION SET SUCCESSFULLY for user: {}", userEmail);
                }
            }

        } catch (io.jsonwebtoken.security.SignatureException e) {
            logger.error("Invalid JWT signature: {}", e.getMessage());
            sendErrorResponse(response, "Invalid token signature", HttpServletResponse.SC_UNAUTHORIZED);
            return;
        } catch (io.jsonwebtoken.MalformedJwtException e) {
            logger.error("Malformed JWT: {}", e.getMessage());
            sendErrorResponse(response, "Malformed token", HttpServletResponse.SC_UNAUTHORIZED);
            return;
        } catch (Exception e) {
            logger.error("JWT Authentication Exception: {}", e.getMessage(), e);
            sendErrorResponse(response, "Authentication failed", HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        filterChain.doFilter(request, response);
    }


    private AuthResponse performAutoRefresh(String sessionId, Long userId, UserDetails userDetails, HttpServletResponse response) {
        try {
            logger.info("Starting auto-refresh for session : {}, userId : {}", sessionId, userId);

            // validate session exists and is active
            UserSessionRedis session = sessionManagementService.findActiveSessionById(sessionId).orElse(null);
            if (session == null) {
                logger.warn("Session not found : {}", sessionId);
                return null;
            }

            if (!session.getUserId().equals(userId)) {
                logger.warn("Session userId mismatch. Expected: {}, Got: {}", session.getUserId(), userId);
                return null;
            }

            // get user from database
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                logger.warn("User not found: {}", userId);
                return null;
            }
            logger.debug("User loaded : {}", user.getEmail());

            // get latest valid refresh token
            RefreshToken refreshToken = refreshTokenService.findLatestValidRefreshToken(userId).orElse(null);
            if (refreshToken == null) {
                logger.warn("No valid refresh token found for user : {}", userId);
                return null;
            }

            // check if refresh token is expired
            try {
                refreshToken = refreshTokenService.verifyRefreshExpiration(refreshToken);
            } catch (Exception e) {
                logger.warn("Refresh token expired for user: {}", userId);
                return null;
            }

            // update refresh token activity timestamp
            refreshToken.setLastActivityTime(Instant.now());
            refreshTokenRepository.save(refreshToken);

            // generate new access token
            String newAccessToken = jwtService.generateAccessToken(userDetails, userId, user.getTokenVersion(), sessionId);
            logger.info("Generated new access token for user : {}", user.getEmail());

            // set new access token in http only cookie
            Cookie cookie = new Cookie("access_token", newAccessToken);
            cookie.setHttpOnly(true);
            cookie.setSecure(false);
            cookie.setPath("/api");
            cookie.setMaxAge((int) (accessTokenExpiration / 1000));
            cookie.setAttribute("SameSite", "Strict");

            response.addCookie(cookie);

            logger.debug("New access token cookie set.");

            // update session activity
            sessionManagementService.updateSessionActivity(sessionId, userId);

            logger.info("Auto-refresh completed successfully for user : {}", user.getEmail());

            return AuthResponse.builder()
                    .accessToken(newAccessToken)
                    .sessionId(sessionId)
                    .expiresIn(accessTokenExpiration / 1000)
                    .build();

        } catch (Exception e) {
            logger.error("Auto-refresh failed : {}", e.getMessage(), e);
            return null;
        }
    }

    private void sendErrorResponse(HttpServletResponse response, String message, int status) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(String.format("{\"success\":false,\"message\":\"%s\"}", message.replace("\"", "\\\"")));
    }
}
