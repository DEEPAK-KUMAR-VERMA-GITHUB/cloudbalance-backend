package com.cloudkeeper.cloudbalance_backend.service;

import com.cloudkeeper.cloudbalance_backend.entity.User;
import com.cloudkeeper.cloudbalance_backend.entity.UserSession;
import com.cloudkeeper.cloudbalance_backend.logging.Logger;
import com.cloudkeeper.cloudbalance_backend.logging.LoggerFactory;
import com.cloudkeeper.cloudbalance_backend.logging.annotation.Loggable;
import com.cloudkeeper.cloudbalance_backend.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SessionManagementService {

    private final UserSessionRepository sessionRepository;
    private final Logger logger = LoggerFactory.getLogger(SessionManagementService.class);
    @Value("${security.idle-timeout}")
    private long idleTimeout;

    @Transactional
    public UserSession createSession(User user, String deviceInfo, String ipAddress) {

        // get http session id from current request context
        String sessionId = RequestContextHolder.currentRequestAttributes().getSessionId();

        logger.info("Creating session: {} for user: {}", sessionId, user.getEmail());

        // deactivate all existing sessions (Single device login only)
        sessionRepository.deactivateAllSessionsByUser(user);

        UserSession userSession = UserSession.builder()
                .user(user)
                .sessionId(sessionId)
                .deviceInfo(deviceInfo)
                .ipAddress(ipAddress)
                .loginTime(Instant.now())
                .lastActivityTime(Instant.now())
                .active(true)
                .build();

        UserSession saved = sessionRepository.save(userSession);
        logger.info("✅ Session created successfully: {}", sessionId);

        return saved;
    }

    @Transactional
    public void updateSessionActivity(String sessionId) {
        sessionRepository.findBySessionId(sessionId).ifPresent(session -> {
            session.setLastActivityTime(Instant.now());
            sessionRepository.save(session);
            logger.info("Session invalidated : {}", sessionId);
        });
    }


    @Transactional
    public void invalidateSession(String sessionId) {
        sessionRepository.findBySessionId(sessionId).ifPresent(session -> {
            session.setActive(false);
            sessionRepository.save(session);
            logger.info("Session invalidated: {}", sessionId);
        });
    }

    @Transactional
    public void invalidateAllUserSessions(User user) {
        sessionRepository.deactivateAllSessionsByUser(user);
        logger.info("All sessions invalidated for user : {}", user.getEmail());
    }

    @Loggable
    public boolean isSessionValid(String sessionId) {
        // 1. Find session
        Optional<UserSession> sessionOpt = sessionRepository.findBySessionId(sessionId);

        // 2. If session doesn't exist → invalid
        if (sessionOpt.isEmpty()) {
            logger.warn("Session not found: {}", sessionId);
            return false;
        }

        UserSession session = sessionOpt.get();

        // 3. If already marked inactive → invalid
        if (!session.getActive()) {
            logger.warn("Session already inactive: {}", sessionId);
            return false;
        }

        // 4. Check idle timeout
        Instant idleThreshold = Instant.now().minusMillis(idleTimeout);

        if (session.getLastActivityTime().isBefore(idleThreshold)) {
            logger.info("Session expired due to inactivity: {} (user: {})",
                    sessionId, session.getId());

            // Mark as inactive
            session.setActive(false);
            sessionRepository.save(session);

            return false; // ← Session expired
        }

        // 5. Session is valid
        return true;
    }


    public List<UserSession> getActiveUserSessions(User user) {
        return sessionRepository.findActiveSessionsByUser(user);
    }

    // cleanup inactive sessions in every 30 minutes
    @Scheduled(fixedRate = 1800000)
    @Transactional
    public void cleanupInactiveSessions() {
        Instant threshold = Instant.now().minusMillis(idleTimeout);
        sessionRepository.deleteInactiveSessions(threshold);
        logger.info("Inactive sessions cleaned up.");
    }

}


















