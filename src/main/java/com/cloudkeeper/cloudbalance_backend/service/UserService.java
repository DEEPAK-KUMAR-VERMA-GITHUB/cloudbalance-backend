package com.cloudkeeper.cloudbalance_backend.service;

import com.cloudkeeper.cloudbalance_backend.dto.request.UserCreateRequest;
import com.cloudkeeper.cloudbalance_backend.dto.request.UserUpdateRequest;
import com.cloudkeeper.cloudbalance_backend.dto.response.UserResponse;
import com.cloudkeeper.cloudbalance_backend.entity.User;
import com.cloudkeeper.cloudbalance_backend.entity.UserRole;
import com.cloudkeeper.cloudbalance_backend.exception.ResourceNotFoundException;
import com.cloudkeeper.cloudbalance_backend.repository.UserRepository;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapToResponse).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("No user found with id : " + id));
        return mapToResponse(user);
    }

    public UserResponse createUser(UserCreateRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists : " + request.getEmail());
        }

        // convert role strings to UserRole enum
        Set<UserRole> roles = request.getRoles().stream()
                .map(UserRole::fromDisplayName)
                .collect(Collectors.toSet());

        // Generate temporary password
        // TODO : In production send via email
        String tempPassword = UUID.randomUUID().toString().substring(0, 8);

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(tempPassword))
                .roles(roles)
                .active(true)
                .build();

        User savedUser = userRepository.save(user);
        log.info("Created user : {} with temp password : {}", savedUser.getEmail(), tempPassword);

        return mapToResponse(savedUser);
    }

    @Transactional
    public UserResponse updateUser(Long id, UserUpdateRequest request) {
        User user = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("No user found with id : " + id));
        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            user.setLastName(request.getLastName());
        }
        if (request.getRoles() != null && !request.getRoles().isEmpty()) {
            Set<UserRole> roles = request.getRoles().stream()
                    .map(UserRole::fromDisplayName)
                    .collect(Collectors.toSet());
            user.setRoles(roles);
        }
        if (request.getActive() != null) {
            user.setActive(request.getActive());
        }

        User updateUser = userRepository.save(user);

        return mapToResponse(updateUser);
    }

    @Transactional
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User not found with id : " + id);
        }
        userRepository.deleteById(id);
        log.info("Deleted user with id : {}", id);
    }


    private UserResponse mapToResponse(@NonNull User user) {
        Set<String> roleDisplayNames = user.getRoles().stream()
                .map(UserRole::getDisplayName)
                .collect(Collectors.toSet());

        return UserResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .roles(roleDisplayNames)
                .active(user.getActive())
                .lastLogin(user.getLastLogin())
                .createdAt(user.getCreatedAt())
                .canPromote(user.getRoles().contains(UserRole.ADMIN))
                .canResend(!user.getActive() || user.getLastLogin() == null)
                .build();
    }

}
