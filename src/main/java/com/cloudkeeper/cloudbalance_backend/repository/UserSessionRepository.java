package com.cloudkeeper.cloudbalance_backend.repository;

import com.cloudkeeper.cloudbalance_backend.entity.User;
import com.cloudkeeper.cloudbalance_backend.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {
    Optional<UserSession> findBySessionId(String sessionId);

    List<UserSession> findByUser(User user);

    @Query("SELECT us FROM UserSession us WHERE us.user = :user AND us.active = true")
    List<UserSession> findActiveSessionsByUser(User user);

    @Modifying
    @Query("UPDATE UserSession us SET us.active = false WHERE us.user = :user")
    void deactivateAllSessionsByUser(User user);

    @Modifying
    @Query("DELETE FROM UserSession us WHERE us.lastActivityTime < :threshold")
    void deleteInactiveSessions(Instant threshold);

    @Query("Select s FROM UserSession s WHERE s.sessionId = :sessionId AND s.active = true")
    Optional<UserSession> findBySessionIdAndActiveTrue(@Param("sessionId") String sessionId);
}
