package com.cloudkeeper.cloudbalance_backend.config;

import com.cloudkeeper.cloudbalance_backend.logging.Logger;
import com.cloudkeeper.cloudbalance_backend.logging.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

@Component
@Order(1)
@RequiredArgsConstructor
public class ConnectionTestRunner implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(ConnectionTestRunner.class);
    private final DataSource dataSource;
    private final RedisTemplate<String, String> redisTemplate;


    @Override
    public void run(String... args) throws Exception {
        testDatabaseConnection();
        testRedisConnection();
    }

    private void testRedisConnection() {
        try {
            redisTemplate.opsForValue().set("connection:test", "SUCCESS");
            String result = redisTemplate.opsForValue().get("connection:test");
            redisTemplate.delete("connection:test");

            if ("SUCCESS".equals(result)) {
                logger.info("✓ Redis connection successful!");
                logger.info("  Host: localhost:6379");
            }
        } catch (Exception e) {
            logger.error("✗ Redis connection failed!", e);
            logger.error("  Please check if Redis is running on localhost:6379");
            logger.error("  Error: {}", e.getMessage());
        }
    }

    private void testDatabaseConnection() {
        try (Connection connection = dataSource.getConnection()) {
            logger.info("✓ PostgreSQL connection successful!");
            logger.info("  Database: {}", connection.getCatalog());
            logger.info("  URL: {}", connection.getMetaData().getURL());
        } catch (Exception e) {
            logger.error("✗ PostgreSQL connection failed!", e);
            logger.error("  Please check if PostgreSQL is running on localhost:5432");
            logger.error("  Error: {}", e.getMessage());
        }
    }
}
