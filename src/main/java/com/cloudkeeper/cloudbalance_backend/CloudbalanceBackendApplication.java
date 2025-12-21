package com.cloudkeeper.cloudbalance_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CloudbalanceBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(CloudbalanceBackendApplication.class, args);
    }

}
