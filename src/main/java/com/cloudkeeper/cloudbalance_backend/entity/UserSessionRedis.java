package com.cloudkeeper.cloudbalance_backend.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;
import org.springframework.data.redis.core.index.Indexed;

import java.io.Serializable;
import java.time.Instant;

@RedisHash(value = "user_sessions", timeToLive = 1800)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSessionRedis implements Serializable {

    @Id
    private String sessionId; // primary key (http session id or UUID)

    @Indexed    // required for findByUserId query
    private Long userId;
    private String email;
    private String deviceInfo;
    private String ipAddress;

    private Instant loginTime;
    private Instant lastActivityTime;
    private Boolean active;

    private String deviceName;  // support for multi-device

    @TimeToLive
    private Long ttl;  // auto-expires after ttl seconds

}
