package com.cloudkeeper.cloudbalance_backend.repository.redis;

import com.cloudkeeper.cloudbalance_backend.entity.UserSessionRedis;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserSessionRedisRepository extends CrudRepository<UserSessionRedis, String> {
    // find all sessions for a user
    List<UserSessionRedis> findByUserId(Long userId);
    // find by session id
    Optional<UserSessionRedis> findBySessionId(String sessionId);

}
