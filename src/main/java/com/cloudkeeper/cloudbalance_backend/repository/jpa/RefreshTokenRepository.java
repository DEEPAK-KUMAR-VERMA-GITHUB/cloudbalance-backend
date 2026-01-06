package com.cloudkeeper.cloudbalance_backend.repository.jpa;

import com.cloudkeeper.cloudbalance_backend.entity.RefreshToken;
import com.cloudkeeper.cloudbalance_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    @Query("SELECT rt FROM RefreshToken rt JOIN FETCH rt.user WHERE rt.token = :token")
    Optional<RefreshToken> findByToken(String token);

    List<RefreshToken> findByUser(User user);

    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.user = :user")
    void deleteByUser(User user);

    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiryDate < :now")
    void deleteExpiredTokens(Instant now);

    @Query("SELECT rt FROM RefreshToken rt WHERE rt.user = :user AND rt.revoked = false")
    List<RefreshToken> findActiveTokensByUser(User user);

    @Query("SELECT rt FROM RefreshToken rt JOIN FETCH rt.user WHERE rt.user.id = :userId AND rt.revoked = false ORDER BY rt.createdAt DESC")
    Optional<RefreshToken> findTopByUserIdAndRevokedFalseOrderByCreatedAtDesc(@Param("userId") Long userId);

    @Query("SELECT rt FROM RefreshToken rt WHERE rt.user = :user AND rt.deviceInfo = :deviceInfo AND rt.revoked = false")
    Optional<RefreshToken> findByUserAndDeviceInfoAndRevokedFalse(User user, String deviceInfo);
}
