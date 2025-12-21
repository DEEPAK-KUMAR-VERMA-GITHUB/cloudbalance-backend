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
    protected void doFilterInternal(@NonNull HttpServletRequest request,@NonNull HttpServletResponse response,@NonNull FilterChain filterChain) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            final String jwt = authHeader.split("Bearer ")[1];

            // check if token is blacklisted
            if(tokenBlackListService.isTokenBlacklisted(jwt)){
                logger.warn("Blacklisted token attempted: {}", jwt.substring(0,10) + "...");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\":\"Token has been revoked\"}");
                return;
            }

            final String userEmail = jwtService.extractUsername(jwt);
            final Long userId = jwtService.extractUserId(jwt);

            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = this.appUserDetailsService.loadUserByUsername(userEmail);

                // validate token including version check
                if (jwtService.isTokenValid(jwt, userDetails, userId)) {

                    // check session validity
                    String sessionId = request.getSession().getId();
                    if(!sessionManagementService.isSessionValid(sessionId)){
                        logger.warn("Invalid or expired session for user: {}", userEmail);
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.getWriter().write("{\"error\":\"Session expired. Please login again.\"}");
                        return;
                    }

                    // update session activity
                    sessionManagementService.updateSessionActivity(sessionId);
                    UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authenticationToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                }
            }
        } catch (Exception e) {
            logger.error("Cannot set user authentication : {}", e);
        }

        filterChain.doFilter(request, response);
    }
}
