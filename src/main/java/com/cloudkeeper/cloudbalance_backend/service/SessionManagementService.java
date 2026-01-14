package com.cloudkeeper.cloudbalance_backend.service;

import com.cloudkeeper.cloudbalance_backend.entity.User;
import com.cloudkeeper.cloudbalance_backend.entity.UserSessionRedis;
import com.cloudkeeper.cloudbalance_backend.exception.MaxSessionsReachedException;
import com.cloudkeeper.cloudbalance_backend.logging.Logger;
import com.cloudkeeper.cloudbalance_backend.logging.LoggerFactory;
import com.cloudkeeper.cloudbalance_backend.logging.annotation.Loggable;
import com.cloudkeeper.cloudbalance_backend.repository.redis.UserSessionRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SessionManagementService {

    private final UserSessionRedisRepository sessionRedisRepository;
    private final Logger logger = LoggerFactory.getLogger(SessionManagementService.class);
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${app.security.session.ttl-seconds}")
    private long sessionTtlSeconds;
    @Value("${app.security.session.max-concurrent-sessions}")
    private int maxConcurrentSessions;
    @Value("${app.security.session.idle-timeout}")
    private long idleTimeOut;
    @Value("${app.security.session.force-logout-oldest}")
    private boolean forceLogoutOldest;


    // create new redis session with max limit enforcement
    public UserSessionRedis createSession(User user, String deviceInfo, String ipAddress) {

        Instant now = Instant.now();
        String deviceName = extractDeviceName(deviceInfo);

        logger.warn("maxCurrentSessions : {} and idleTimeOut : {}, and forceLogoutOldest : {}",
                maxConcurrentSessions, idleTimeOut, forceLogoutOldest);

        logger.info("Creating Redis session for user : {} on device: {}", user.getEmail(), deviceName);

        // Get all active sessions for this user (EXCLUDE current sessionId)
        List<UserSessionRedis> activeSessions = getActiveUserSessions(user.getId());

        logger.info("User {} currently has {} active sessions (max allowed: {}) : {}",
                user.getEmail(), activeSessions.size(), maxConcurrentSessions, activeSessions.stream().map(UserSessionRedis::getSessionId).toList());

        // Check concurrent session limit FIRST (BEFORE reactivating)
        if (activeSessions.size() >= maxConcurrentSessions) {
            logger.warn("Max sessions ({}) reached for user: {}", maxConcurrentSessions, user.getEmail());
            if (forceLogoutOldest) {
                // auto-logout oldest session
                UserSessionRedis oldest = activeSessions.stream()
                        .min(Comparator.comparing(UserSessionRedis::getLoginTime))
                        .orElseThrow(() -> new IllegalStateException("No sessions found to remove"));

                logger.info("🔄 Force logout enabled - removing oldest session: {}", oldest.getSessionId());

                invalidateSession(oldest.getSessionId(), user.getId());
            } else {
                // Throw exception to prompt user
                logger.error("❌ Session limit reached! User: {}, Active: {}, Max: {}",
                        user.getEmail(), activeSessions.size(), maxConcurrentSessions);

                throw new MaxSessionsReachedException(
                        "Maximum " + maxConcurrentSessions + " device(s) allowed. " +
                                "Please logout from another device or use force-login."
                );
            }
        }

        // Create new session (limit already checked above)
        String sessionId = generateSessionId();
        UserSessionRedis newSession = UserSessionRedis.builder()
                .sessionId(sessionId)
                .userId(user.getId())
                .email(user.getEmail())
                .deviceInfo(deviceInfo)
                .deviceName(deviceName)
                .ipAddress(ipAddress)
                .loginTime(now)
                .lastActivityTime(now)
                .active(true)
                .ttl(idleTimeOut / 1000)
                .build();

        UserSessionRedis saved = sessionRedisRepository.save(newSession);

        logger.info("New Redis session created: {} for user: {}", sessionId, user.getEmail());

        return saved;
    }

    private String generateSessionId() {
        return UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }

    @Transactional
    public void updateSessionActivity(String sessionId, Long userId) {
        sessionRedisRepository.findBySessionId(sessionId).ifPresent(session -> {
            if (session.getUserId().equals(userId) && session.getActive()) {
                session.setLastActivityTime(Instant.now());
                session.setTtl(idleTimeOut / 1000);
                sessionRedisRepository.save(session);
                logger.debug("Updated activity for session : {}", sessionId);
            }
        });
    }

    // check if session is valid or not
    @Loggable
    @Transactional
    public boolean isSessionValid(String sessionId) {
        if (sessionId == null) {
            logger.warn("Session ID is null");
            return false;
        }

        // Find session
        Optional<UserSessionRedis> sessionOpt = sessionRedisRepository.findBySessionId(sessionId);

        // If session doesn't exist → invalid
        if (sessionOpt.isEmpty()) {
            logger.warn("Session not found in Redis: {}", sessionId);
            return false;
        }

        UserSessionRedis session = sessionOpt.get();

        // If already marked inactive → invalid
        if (!session.getActive()) {
            logger.debug("Session already inactive in Redis: {}", sessionId);
            return false;
        }

        // check idle timeout
        Instant idleThreshold = Instant.now().minusMillis(idleTimeOut);
        if (session.getLastActivityTime().isBefore(idleThreshold)) {
            logger.info("Session expired due to inactivity : {}", sessionId);
            session.setActive(false);
            session.setTtl(60L);
            sessionRedisRepository.save(session);

            return false;
        }

        // Session is valid
        logger.debug("Session valid in Redis : {}", sessionId);
        return true;
    }

    @Loggable
    @Transactional(readOnly = true)
    public Optional<UserSessionRedis> findActiveSessionById(String sessionId) {
        return sessionRedisRepository.findBySessionId(sessionId).filter(UserSessionRedis::getActive);
    }

    // get all active sessions for a user
    @Transactional(readOnly = true)
    public List<UserSessionRedis> getActiveUserSessions(Long userId) {
        try {
            // Use Spring Data Redis Repository's built-in indexing
            List<UserSessionRedis> allSessions = sessionRedisRepository.findByUserId(userId);

            // Filter active sessions
            List<UserSessionRedis> activeSessions = allSessions.stream()
                    .filter(UserSessionRedis::getActive)
                    .collect(Collectors.toList());

            logger.info("Retrieved {} active sessions for userId: {}", activeSessions.size(), userId);
            return activeSessions;

        } catch (Exception e) {
            logger.error("Error fetching sessions for userId {}: {}", userId, e.getMessage(), e);
            return Collections.emptyList();
        }
        
    }

    // deactivate specific session
    @Transactional
    public void invalidateSession(String sessionId, Long userId) {
        sessionRedisRepository.findBySessionId(sessionId).ifPresent(session -> {
            if (session.getUserId().equals(userId)) {
                sessionRedisRepository.delete(session);
                logger.info("Deleted session: {} for userId : {}", sessionId, userId);
            }
        });
    }

    @Transactional
    public void invalidateAllUserSessions(Long userId) {
        List<UserSessionRedis> allSessions = sessionRedisRepository.findByUserId(userId);
        sessionRedisRepository.deleteAll(allSessions);
        logger.info("Deleted all {} sessions for userId : {}", allSessions.size(), userId);
    }

    private String extractDeviceName(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return "Unknown Device";
        }

        // Check for mobile devices
        if (userAgent.contains("Mobile") || userAgent.contains("Android") || userAgent.contains("iPhone")) {
            if (userAgent.contains("Android")) return "Android Mobile";
            if (userAgent.contains("iPhone")) return "iPhone";
            if (userAgent.contains("iPad")) return "iPad";
            return "Mobile Device";
        }

        // Check for browsers
        if (userAgent.contains("Firefox")) return "Firefox on " + getOS(userAgent);
        if (userAgent.contains("Chrome") && !userAgent.contains("Edg")) return "Chrome on " + getOS(userAgent);
        if (userAgent.contains("Safari") && !userAgent.contains("Chrome")) return "Safari on " + getOS(userAgent);
        if (userAgent.contains("Edg")) return "Edge on " + getOS(userAgent);

        return "Desktop";
    }

    // extracts os from user-agent string
    private String getOS(String userAgent) {
        if (userAgent.contains("Windows")) return "Windows";
        if (userAgent.contains("Mac OS")) return "macOS";
        if (userAgent.contains("Linux")) return "Linux";
        if (userAgent.contains("Ubuntu")) return "Ubuntu";
        if (userAgent.contains("Android")) return "Android";
        if (userAgent.contains("iOS")) return "iOS";
        return "Unknown OS";
    }
}


















