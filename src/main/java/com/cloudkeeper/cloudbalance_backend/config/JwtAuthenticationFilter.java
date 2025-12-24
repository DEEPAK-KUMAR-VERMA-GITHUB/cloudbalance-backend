package com.cloudkeeper.cloudbalance_backend.config;

import com.cloudkeeper.cloudbalance_backend.logging.Logger;
import com.cloudkeeper.cloudbalance_backend.logging.LoggerFactory;
import com.cloudkeeper.cloudbalance_backend.service.AppUserDetailsService;
import com.cloudkeeper.cloudbalance_backend.service.JwtService;
import com.cloudkeeper.cloudbalance_backend.service.SessionManagementService;
import com.cloudkeeper.cloudbalance_backend.service.TokenBlackListService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AppUserDetailsService appUserDetailsService;
    private final TokenBlackListService tokenBlackListService;
    private final SessionManagementService sessionManagementService;
    private final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);


    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        logger.info("🔍 JWT Filter - AuthHeader: {}", authHeader != null ? authHeader.substring(0, 20) + "..." : "NULL");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            logger.info("❌ No Bearer token");
            filterChain.doFilter(request, response);
            return;
        }

        try {
            final String jwt = authHeader.split("Bearer ")[1];
            logger.info("🔍 JWT Extracted: {}", jwt.substring(0, 20) + "...");

            // 1️⃣ CHECK BLACKLIST
            if (tokenBlackListService.isTokenBlacklisted(jwt)) {
                logger.warn("❌ BLACKLISTED token: {}", jwt.substring(0, 10) + "...");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\":\"Token has been revoked\"}");
                return;
            }
            logger.info("✅ Token NOT blacklisted");

            final String userEmail = jwtService.extractUsername(jwt);
            final Long userId = jwtService.extractUserId(jwt);
            logger.info("✅ Extracted - Email: {}, UserId: {}", userEmail, userId);

            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = this.appUserDetailsService.loadUserByUsername(userEmail);
                logger.info("✅ UserDetails loaded: {}", userDetails.getUsername());

                // 2️⃣ CHECK TOKEN VALIDITY
                if (!jwtService.isTokenValid(jwt, userDetails, userId)) {
                    logger.warn("❌ Token INVALID (version/expiry check failed)");
                    filterChain.doFilter(request, response);
                    return;
                }
                logger.info("✅ Token VALID");

                // 3️⃣ SESSION CHECK (BIG PROBLEM!)
                String sessionId = request.getSession().getId();
                logger.info("🔍 Session ID: {}", sessionId);

                if (!sessionManagementService.isSessionValid(sessionId)) {
                    logger.warn("❌ SESSION INVALID: {}", sessionId);
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write("{\"error\":\"Session expired. Please login again.\"}");
                    return;
                }
                logger.info("✅ Session VALID");

                // UPDATE SESSION
                sessionManagementService.updateSessionActivity(sessionId);

                // ✅ SET AUTHENTICATION (THIS NEVER REACHES!)
                UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities()
                );
                authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                logger.info("🎉 AUTHENTICATION SET SUCCESSFULLY!!!");

            } else {
                logger.info("⏭️ Skipping - already authenticated or no email");
            }
        } catch (Exception e) {
            logger.error("💥 JWT EXCEPTION: {}", e.getMessage(), e);
        }

        filterChain.doFilter(request, response);
    }
}
