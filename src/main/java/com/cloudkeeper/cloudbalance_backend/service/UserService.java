package com.cloudkeeper.cloudbalance_backend.service;

import com.cloudkeeper.cloudbalance_backend.dto.request.UserCreateRequest;
import com.cloudkeeper.cloudbalance_backend.dto.request.UserUpdateRequest;
import com.cloudkeeper.cloudbalance_backend.dto.response.PagedResponse;
import com.cloudkeeper.cloudbalance_backend.dto.response.UserResponse;
import com.cloudkeeper.cloudbalance_backend.entity.User;
import com.cloudkeeper.cloudbalance_backend.entity.UserRole;
import com.cloudkeeper.cloudbalance_backend.exception.ResourceNotFoundException;
import com.cloudkeeper.cloudbalance_backend.logging.Logger;
import com.cloudkeeper.cloudbalance_backend.logging.LoggerFactory;
import com.cloudkeeper.cloudbalance_backend.repository.jpa.UserRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Logger logger = LoggerFactory.getLogger(UserService.class);

    @Transactional(readOnly = true)
    public PagedResponse<UserResponse> getAllUsers(
            int page,
            int size,
            String sortBy,
            String sortDir,
            String search,
            Boolean active,
            UserRole role
    ) {
        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);

        Page<User> userPage;

        if (search != null && !search.isBlank()) {
            userPage = userRepository.searchByNameOrEmail(search.trim(), pageable);
        } else if (active != null && role != null) {
            userPage = userRepository.findByActiveAndRole(active, role, pageable);
        } else if (active != null) {
            userPage = userRepository.findByActive(active, pageable);
        } else if (role != null) {
            userPage = userRepository.findByRole(role, pageable);
        } else {
            userPage = userRepository.findAll(pageable);
        }

        var content = userPage.getContent()
                .stream()
                .map(this::mapToResponse)
                .toList();

        return PagedResponse.<UserResponse>builder()
                .content(content)
                .page(userPage.getNumber())
                .size(userPage.getSize())
                .totalElements(userPage.getTotalElements())
                .first(userPage.isFirst())
                .last(userPage.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("No user found with id : " + id));
        return mapToResponse(user);
    }

    @Transactional
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
        logger.info("Created user : {} with temp password : {}", savedUser.getEmail(), tempPassword);

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
        logger.info("Deleted user with id : {}", id);
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

    public UserResponse deactivateUser(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found with id : " + userId));
        if (!Boolean.FALSE.equals(user.getActive())) {
            user.setActive(false);
        }
        User savedUser = userRepository.save(user);
        logger.info("Deactivated user with id : {}", userId);
        return mapToResponse(savedUser);
    }

    public UserResponse activateUser(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found with id : " + userId));
        if (!Boolean.TRUE.equals(user.getActive())) {
            user.setActive(true);
        }
        User savedUser = userRepository.save(user);
        logger.info("Activated user with id : {}", userId);
        return mapToResponse(savedUser);
    }
}
