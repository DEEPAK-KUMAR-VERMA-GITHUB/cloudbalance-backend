package com.cloudkeeper.cloudbalance_backend.service;

import com.cloudkeeper.cloudbalance_backend.logging.Logger;
import com.cloudkeeper.cloudbalance_backend.logging.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class TokenBlackListService {
    private final RedisTemplate<String, String> redisTemplate;
    private static final String BLACKLIST_PREFIX = "blacklist:token:";
    private static final String USER_TOKEN_VERSION_PREFIX = "user:token:version:";
    private static final Logger logger = LoggerFactory.getLogger(TokenBlackListService.class);

    public void blacklistToken(String token, long expirationTimeMs) {
        String key = BLACKLIST_PREFIX + token;
        redisTemplate.opsForValue().set(key, "revoked", Duration.ofMillis(expirationTimeMs));
        logger.info("Token blacklisted : {}", token.substring(0, 10) + "...");
    }

    public boolean isTokenBlacklisted(String token) {
        String key = BLACKLIST_PREFIX + token;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public void incrementUserTokenVersion(Long userId) {
        String key = USER_TOKEN_VERSION_PREFIX + userId;
        redisTemplate.opsForValue().increment(key);
        logger.info("Token version incremented for user: {}", userId);
    }

    public Integer getUserTokenVersion(Long userId) {
        String key = USER_TOKEN_VERSION_PREFIX + userId;
        String version = redisTemplate.opsForValue().get(key);
        return version != null ? Integer.parseInt(version) : 0;
    }

    public void setUserTokenVersion(Long userId, Integer version) {
        String key = USER_TOKEN_VERSION_PREFIX + userId;
        redisTemplate.opsForValue().set(key, version.toString());
    }

}


























