package com.cloudkeeper.cloudbalance_backend.service;

import com.cloudkeeper.cloudbalance_backend.entity.User;
import com.cloudkeeper.cloudbalance_backend.entity.UserSession;
import com.cloudkeeper.cloudbalance_backend.logging.Logger;
import com.cloudkeeper.cloudbalance_backend.logging.LoggerFactory;
import com.cloudkeeper.cloudbalance_backend.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SessionManagementService {

    private final UserSessionRepository sessionRepository;
    private final Logger logger = LoggerFactory.getLogger(SessionManagementService.class);
    @Value("${security.idle-timeout}")
    private long idleTimeout;

    @Transactional
    public UserSession createSession(User user, String deviceInfo, String ipAddress) {
        // deactivate all existing sessions (Single device login only)
        sessionRepository.deactivateAllSessionsByUser(user);

        UserSession userSession = UserSession.builder()
                .user(user)
                .sessionId(UUID.randomUUID().toString())
                .deviceInfo(deviceInfo)
                .ipAddress(ipAddress)
                .loginTime(Instant.now())
                .lastActivityTime(Instant.now())
                .active(true)
                .build();

        return sessionRepository.save(userSession);
    }

    @Transactional
    public void updateSessionActivity(String sessionId) {
        sessionRepository.findBySessionId(sessionId).ifPresent(session -> {
            session.setActive(false);
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

    public boolean isSessionValid( String sessionId) {
        return sessionRepository.findBySessionId(sessionId)
                .map(session -> {
                    if (!session.getActive()) {
                        return false;
                    }
                    Instant threshold = Instant.now().minusMillis(idleTimeout);
                    if (session.getLastActivityTime().isBefore(threshold)) {
                        invalidateSession(sessionId);
                        return false;
                    }
                    return true;
                }).orElse(false);
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


















