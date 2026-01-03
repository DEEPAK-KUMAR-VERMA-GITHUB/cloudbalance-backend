package com.cloudkeeper.cloudbalance_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;

@SpringBootApplication
@EnableJpaRepositories(
        basePackages = "com.cloudkeeper.cloudbalance_backend.repository.jpa"
)
public class CloudbalanceBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(CloudbalanceBackendApplication.class, args);


    }

}
