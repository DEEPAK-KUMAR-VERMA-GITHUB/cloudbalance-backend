package com.cloudkeeper.cloudbalance_backend.config;

import com.cloudkeeper.cloudbalance_backend.entity.User;
import com.cloudkeeper.cloudbalance_backend.entity.UserRole;
import com.cloudkeeper.cloudbalance_backend.repository.jpa.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        if (userRepository.count() == 0) {
            log.info("Initializing default users...");

            User admin = User.builder()
                    .firstName("Deepak")
                    .lastName("Kumar Verma")
                    .email("deepak@gmail.com")
                    .password(passwordEncoder.encode("SecurePassword@123"))
                    .roles(Set.of(UserRole.ADMIN))
                    .active(true)
                    .build();

            userRepository.save(admin);
            log.info("Default admin user created : deepak@gmail.com / SecurePassword@123");
        }
    }
}
