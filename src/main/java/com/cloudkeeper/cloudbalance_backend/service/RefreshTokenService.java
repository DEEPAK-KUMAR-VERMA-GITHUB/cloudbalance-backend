package com.cloudkeeper.cloudbalance_backend.service;

import com.cloudkeeper.cloudbalance_backend.entity.RefreshToken;
import com.cloudkeeper.cloudbalance_backend.entity.User;
import com.cloudkeeper.cloudbalance_backend.exception.TokenRefreshException;
import com.cloudkeeper.cloudbalance_backend.logging.Logger;
import com.cloudkeeper.cloudbalance_backend.logging.LoggerFactory;
import com.cloudkeeper.cloudbalance_backend.repository.jpa.RefreshTokenRepository;
import com.cloudkeeper.cloudbalance_backend.repository.jpa.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public RefreshToken createRefreshToken(Long userId, String deviceInfo, String ipAddress, String sessionId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

        // if refresh token already exists
        Optional<RefreshToken> existingToken = refreshTokenRepository.findByUserAndDeviceInfoAndRevokedFalse(user, deviceInfo);

        if (existingToken.isPresent()) {
            // Reuse existing token for this device
            RefreshToken token = existingToken.get();
            token.setLastActivityTime(Instant.now());
            token.setExpiryDate(Instant.now().plusMillis(refreshTokenExpireDurationMs));
            return refreshTokenRepository.save(token);
        }

        // create new refresh token for this device/session
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

    @Transactional(readOnly = true)
    public Optional<RefreshToken> findLatestValidRefreshToken(Long userId){
        Optional<RefreshToken> refreshToken = refreshTokenRepository.findTopByUserIdAndRevokedFalseOrderByCreatedAtDesc(userId);

        if(refreshToken.isEmpty()){
            logger.warn("No valid refresh token found for user: {}", userId);
            return Optional.empty();
        }

        return refreshToken;
    }

    @Transactional
    public void revokeRefreshTokenByDevice(Long userId, String deviceInfo) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

        refreshTokenRepository.findByUserAndDeviceInfoAndRevokedFalse(user, deviceInfo).ifPresent(token -> {
                    token.setRevoked(true);
            refreshTokenRepository.save(token);
            logger.info("Refresh token revoked for user: {} device: {}", userId, deviceInfo);
        });
    }

}

















