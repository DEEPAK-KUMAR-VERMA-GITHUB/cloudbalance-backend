package com.cloudkeeper.cloudbalance_backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackages = "com.cloudkeeper.cloudbalance_backend.repository.jpa")
public class JpaConfig {
}
