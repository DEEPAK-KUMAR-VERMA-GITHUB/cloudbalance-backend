package com.cloudkeeper.cloudbalance_backend.service;

import com.cloudkeeper.cloudbalance_backend.dto.request.AwsAccountCreateRequest;
import com.cloudkeeper.cloudbalance_backend.dto.response.AwsAccountResponse;
import com.cloudkeeper.cloudbalance_backend.entity.AwsAccount;
import com.cloudkeeper.cloudbalance_backend.entity.User;
import com.cloudkeeper.cloudbalance_backend.exception.ResourceAlreadyExistsException;
import com.cloudkeeper.cloudbalance_backend.exception.ResourceNotFoundException;
import com.cloudkeeper.cloudbalance_backend.logging.Logger;
import com.cloudkeeper.cloudbalance_backend.logging.LoggerFactory;
import com.cloudkeeper.cloudbalance_backend.repository.jpa.AwsAccountRepository;
import com.cloudkeeper.cloudbalance_backend.repository.jpa.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AwsAccountService {
    private final AwsAccountRepository awsAccountRepository;
    private final UserRepository userRepository;
    private final Logger logger = LoggerFactory.getLogger(AwsAccountService.class);

    @Transactional
    public AwsAccountResponse createAwsAccount(AwsAccountCreateRequest request) {
        logger.info("Creating AWS account with ID : {}", request.getAccountId());

        if (awsAccountRepository.findByAccountId(request.getAccountId()).isPresent()) {
            throw new ResourceAlreadyExistsException("AWS Account ID already exists : " + request.getAccountId());
        }

        AwsAccount account = AwsAccount.builder().accountId(request.getAccountId()).accountAlias(request.getAccountAlias()).roleArn(request.getRoleArn()).active(true).build();

        AwsAccount savedAwsAccount = awsAccountRepository.save(account);

        logger.info("AWS account created successfully : {}", savedAwsAccount.getId());

        return mapToResponse(savedAwsAccount);
    }

    @Transactional(readOnly = true)
    public List<AwsAccountResponse> getAllAwsAccounts() {
        return awsAccountRepository.findAll().stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AwsAccount> getAccountsForUser(Long userId) {
        return awsAccountRepository.findByAssignedUserId(userId);
    }

    @Transactional(readOnly = true)
    public AwsAccountResponse getAccountById(Long accountId) {
        logger.debug("Fetching AWS account by ID: {}", accountId);
        AwsAccount account = awsAccountRepository.findById(accountId).orElseThrow(() -> new ResourceNotFoundException("AWS Account not found with ID: " + accountId));
        return mapToResponse(account);
    }

    @Transactional
    public void deleteAwsAccount(Long id) {
        AwsAccount account = awsAccountRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Account not found."));
        awsAccountRepository.delete(account);
        logger.info("AWS account deleted : {}", account.getAccountId());
    }

    @Transactional(readOnly = true)
    public Long getUserIdByEmail(String email) {
        logger.debug("Looking up user ID for email : {}", email);
        User user = userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User not found with email : " + email));
        logger.debug("Found user ID {} for email {}", user.getId(), email);
        return user.getId();
    }

    private AwsAccountResponse mapToResponse(AwsAccount account) {
        return AwsAccountResponse.builder()
                .id(account.getId())
                .accountId(account.getAccountId())
                .accountAlias(account.getAccountAlias())
                .roleArn(account.getRoleArn())
                .active(account.getActive())
                .assignedUsersCount(account.getAssignedUsers().size())
                .assignedUserEmails(account.getAssignedUsers().stream()
                        .map(User::getEmail)
                        .collect(Collectors.toSet()))
                .createdAt(account.getCreatedAt()).updatedAt(account.getUpdateAt()).build();
    }
}
