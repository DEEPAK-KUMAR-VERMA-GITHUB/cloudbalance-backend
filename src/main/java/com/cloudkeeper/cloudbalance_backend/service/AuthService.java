package com.cloudkeeper.cloudbalance_backend.service;

import com.cloudkeeper.cloudbalance_backend.dto.request.LoginRequest;
import com.cloudkeeper.cloudbalance_backend.dto.response.AuthResponse;
import com.cloudkeeper.cloudbalance_backend.entity.User;
import com.cloudkeeper.cloudbalance_backend.exception.InvalidCredentialsException;
import com.cloudkeeper.cloudbalance_backend.logging.Logger;
import com.cloudkeeper.cloudbalance_backend.logging.LoggerFactory;
import com.cloudkeeper.cloudbalance_backend.logging.annotation.Loggable;
import com.cloudkeeper.cloudbalance_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final AppUserDetailsService appUserDetailsService;
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    @Transactional
    @Loggable(logArgs = false, logResult = true)
    public AuthResponse login(LoginRequest request) {
        try {
            // try to authenticate user
            Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
            logger.info("Authentication successful for: {}", request.getEmail());
            // load user details
            UserDetails userDetails = appUserDetailsService.loadUserByUsername(request.getEmail());

            // generate jwt token
            String token = jwtService.generateToken(userDetails);

            // update last login
            User user = userRepository.findByEmail(request.getEmail()).orElseThrow(() -> new InvalidCredentialsException("User not found"));
            user.setLastLogin(LocalDateTime.now());
            userRepository.save(user);

            logger.info("User logged in successfully : {}", request.getEmail());

            // Get primary role
            String primaryRole = user.getRoles().isEmpty() ? "USER" : user.getRoles().iterator().next().getDisplayName();

            return AuthResponse.builder().token(token).type("Bearer").name(user.getFirstName() + " " + user.getLastName()).email(user.getEmail()).role(primaryRole).build();

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


}
