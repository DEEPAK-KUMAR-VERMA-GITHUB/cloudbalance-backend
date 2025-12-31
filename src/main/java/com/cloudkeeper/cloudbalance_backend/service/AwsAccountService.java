package com.cloudkeeper.cloudbalance_backend.service;

import com.cloudkeeper.cloudbalance_backend.dto.request.AwsAccountCreateRequest;
import com.cloudkeeper.cloudbalance_backend.dto.response.AwsAccountResponse;
import com.cloudkeeper.cloudbalance_backend.entity.AwsAccount;
import com.cloudkeeper.cloudbalance_backend.entity.User;
import com.cloudkeeper.cloudbalance_backend.exception.ResourceAlreadyExistsException;
import com.cloudkeeper.cloudbalance_backend.exception.ResourceNotFoundException;
import com.cloudkeeper.cloudbalance_backend.logging.Logger;
import com.cloudkeeper.cloudbalance_backend.logging.LoggerFactory;
import com.cloudkeeper.cloudbalance_backend.repository.AwsAccountRepository;
import com.cloudkeeper.cloudbalance_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AwsAccountService {
    private final AwsAccountRepository awsAccountRepository;
    private final UserRepository userRepository;
    private final TextEncryptor textEncryptor;
    private final Logger logger = LoggerFactory.getLogger(AwsAccountService.class);

    @Transactional
    public AwsAccountResponse createAwsAccount(AwsAccountCreateRequest request) {
        logger.info("Creating AWS account with ID : {}", request.getAccountId());

        if (awsAccountRepository.existsByAccountId(request.getAccountId())) {
            throw new ResourceAlreadyExistsException("AWS Account ID already exists : " + request.getAccountId());
        }

        AwsAccount account = AwsAccount.builder()
                .accountId(request.getAccountId())
                .accountAlias(request.getAccountAlias())
                .accessKeyId(request.getAccessKeyId())
                .secretAccessKey(textEncryptor.encrypt(request.getSecretAccessKey()))
                .region(request.getRegion())
                .monthlyBudget(request.getMonthlyBudget())
                .active(true)
                .build();

        AwsAccount savedAwsAccount = awsAccountRepository.save(account);

        logger.info("AWS account created successfully : {}", savedAwsAccount.getId());

        return mapToResponse(savedAwsAccount);
    }

    @Transactional(readOnly = true)
    public List<AwsAccountResponse> getAllAwsAccounts() {
        return awsAccountRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AwsAccount> getCustomerAccounts(Long userId) {
        return awsAccountRepository.findActiveAccountsByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Long getUserIdByEmail(String email) {
        logger.debug("Looking up user ID for email : {}", email);
        User user = userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User not found with email : " + email));
        logger.debug("Found user ID {} for email {}", user.getId(), email);
        return user.getId();
    }

    @Transactional(readOnly = true)
    public AwsAccount getAccountById(Long accountId) {
        logger.debug("Fetching AWS account by ID: {}", accountId);

        return awsAccountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("AWS Account not found with ID: " + accountId));
    }

    // Get aws account by aws account id
    @Transactional(readOnly = true)
    public AwsAccount getAccountByAwsAccountId(String awsAccountId) {
        return awsAccountRepository.findByAccountId(awsAccountId).orElseThrow(() -> new ResourceNotFoundException("AWS account not found : " + awsAccountId));
    }

    @Transactional
    public AwsAccountResponse updateAwsAccount(Long accountId, AwsAccountCreateRequest request) {
        logger.info("Updating AWS account ID : {}", accountId);

        AwsAccount account = getAccountById(accountId);

        // update fields
        if (request.getAccountAlias() != null) {
            account.setAccountAlias(request.getAccountAlias());
        }
        if (request.getAccessKeyId() != null) {
            account.setAccessKeyId(request.getAccessKeyId());
        }
        if (request.getSecretAccessKey() != null) {
            account.setSecretAccessKey(textEncryptor.encrypt(request.getSecretAccessKey()));
        }
        if (request.getRegion() != null) {
            account.setRegion(request.getRegion());
        }
        if (request.getMonthlyBudget() != null) {
            account.setMonthlyBudget(request.getMonthlyBudget());
        }

        AwsAccount updatedAccount = awsAccountRepository.save(account);

        logger.info("AWS account updated successfully: {}", accountId);

        return mapToResponse(updatedAccount);

    }

    @Transactional
    public void deactivateAccount(Long accountId) {
        logger.info("Deactivating AWS account ID: {}", accountId);

        AwsAccount account = getAccountById(accountId);
        account.setActive(false);
        awsAccountRepository.save(account);

        logger.info("AWS account deactivated: {}", accountId);
    }

    @Transactional
    public void activateAccount(Long accountId) {
        logger.info("Activating AWS account ID: {}", accountId);

        AwsAccount account = getAccountById(accountId);
        account.setActive(true);
        awsAccountRepository.save(account);

        logger.info("AWS account activated: {}", accountId);
    }


    private AwsAccountResponse mapToResponse(AwsAccount account) {
        return AwsAccountResponse.builder()
                .id(account.getId())
                .accountId(account.getAccountId())
                .accountAlias(account.getAccountAlias())
                .region(account.getRegion())
                .active(account.getActive())
                .monthlyBudget(account.getMonthlyBudget())
                .createdAt(account.getCreatedAt())
                .build();
    }
}
