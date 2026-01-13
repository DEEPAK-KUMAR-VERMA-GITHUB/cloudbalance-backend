package com.cloudkeeper.cloudbalance_backend.config;

import com.snowflake.snowpark_java.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
public class SnowflakeConfig {
    @Value("${snowflake.url:}")
    private String url;
    @Value("${snowflake.username:}")
    private String username;
    @Value("${snowflake.password:}")
    private String password;
    @Value("${snowflake.database:}")
    private String database;
    @Value("${snowflake.schema:}")
    private String schema;
    @Value("${snowflake.warehouse:}")
    private String warehouse;
    @Value("${snowflake.role:}")
    private String role;

    @Bean(destroyMethod = "close")
    public Session snowparkSession() {
        log.info("Initializing Snowpark Session...");
        log.info("Database: {}, Schema: {}, Warehouse: {}", database, schema, warehouse);

        try {
            Map<String, String> properties = new HashMap<>();
            properties.put("URL", url);
            properties.put("USER", username);
            properties.put("PASSWORD", password);
            properties.put("WAREHOUSE", warehouse);
            properties.put("DB", database);
            properties.put("SCHEMA", schema);
            properties.put("ROLE", role);
            properties.put("DISABLE_ARROW_RESULT_FORMAT", "true");
            Session session = Session.builder().configs(properties).create();

            log.info("Snowpark Session created successfully");
            log.info("Connected to Snowflake database: {}.{}", database, schema);

            return session;

        } catch (Exception e) {
            log.error("Failed to create Snowpark Session: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize Snowpark Session", e);
        }
    }
}
