package com.cloudkeeper.cloudbalance_backend.service;

import com.cloudkeeper.cloudbalance_backend.entity.RefreshToken;
import com.cloudkeeper.cloudbalance_backend.entity.User;
import com.cloudkeeper.cloudbalance_backend.exception.TokenRefreshException;
import com.cloudkeeper.cloudbalance_backend.logging.Logger;
import com.cloudkeeper.cloudbalance_backend.logging.LoggerFactory;
import com.cloudkeeper.cloudbalance_backend.logging.annotation.Loggable;
import com.cloudkeeper.cloudbalance_backend.repository.RefreshTokenRepository;
import com.cloudkeeper.cloudbalance_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.swing.text.html.Option;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final TokenBlackListService tokenBlackListService;
    private final Logger logger = LoggerFactory.getLogger(RefreshTokenService.class);
    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpireDurationMs;

    @Transactional
    public RefreshToken createRefreshToken(Long userId, String deviceInfo, String ipAddress) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

        // revoke all existing tokens for single device login
        revokeAllUserTokens(user);
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiryDate(Instant.now().plusMillis(refreshTokenExpireDurationMs))
                .deviceInfo(deviceInfo)
                .ipAddress(ipAddress)
                .tokenVersion(user.getTokenVersion())
                .lastActivityTime(Instant.now())
                .revoked(false)
                .build();

        return refreshTokenRepository.save(refreshToken);
    }

    @Transactional(readOnly = true)
    public RefreshToken verifyRefreshExpiration(RefreshToken refreshToken) {
        if (refreshToken.getExpiryDate().compareTo(Instant.now()) < 0) {
            refreshTokenRepository.delete(refreshToken);
            throw new TokenRefreshException("Refresh token expired. Please Login again.");
        }

        if (refreshToken.getRevoked()) {
            logger.warn("Refresh token already revoked : {}", refreshToken.getToken());
            throw new TokenRefreshException("Refresh token has been revoked.");
        }

        return refreshToken;
    }

    @Transactional(readOnly = true)
    public RefreshToken findByToken(String token) {
        return refreshTokenRepository.findByToken(token).orElseThrow(() -> new TokenRefreshException("Refresh token not found."));
    }

    @Transactional
    public void revokeAllUserTokens(User user) {
        List<RefreshToken> activeTokens = refreshTokenRepository.findActiveTokensByUser(user);
        activeTokens.forEach(token -> {
            token.setRevoked(true);
            tokenBlackListService.blacklistToken(token.getToken(), refreshTokenExpireDurationMs);
        });

        refreshTokenRepository.saveAll(activeTokens);

        // increment token version to invalidate all jwt tokens
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
        tokenBlackListService.setUserTokenVersion(user.getId(), user.getTokenVersion());

        logger.info("All tokens revoked for user : {}", user.getEmail());
    }

    @Transactional
    public void deleteExpiredTokens() {
        refreshTokenRepository.deleteExpiredTokens(Instant.now());
    }

    @Transactional
    public boolean hasActiveSession(User user) {
        List<RefreshToken> activeTokens = refreshTokenRepository.findActiveTokensByUser(user);
        return !activeTokens.isEmpty();
    }

    @Transactional(readOnly = true)
    public Optional<RefreshToken> findLatestValidRefreshToken(Long userId){
        Optional<RefreshToken> refreshToken = refreshTokenRepository.findTopByUserIdAndRevokedFalseOrderByCreatedAtDesc(userId);

        if(refreshToken.isEmpty()){
            logger.warn("No valid refresh token found for user: {}", userId);
            return Optional.empty();
        }

        return refreshToken;
    }

}

















