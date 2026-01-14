package com.cloudkeeper.cloudbalance_backend.service;


import com.cloudkeeper.cloudbalance_backend.dto.request.AwsAccountCreateRequest;
import com.cloudkeeper.cloudbalance_backend.dto.request.BulkAccountAssignmentToUserRequest;
import com.cloudkeeper.cloudbalance_backend.dto.response.AccountAssignmentResult;
import com.cloudkeeper.cloudbalance_backend.dto.response.AwsAccountResponse;
import com.cloudkeeper.cloudbalance_backend.dto.response.BulkAccountAssignmentResponse;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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


    //     Assign multiple AWS accounts to a single user
    @Transactional
    public BulkAccountAssignmentResponse assignMultipleAccountsToUser(
            BulkAccountAssignmentToUserRequest request) {

        logger.info("Starting bulk assignment: {} accounts to user {}",
                request.getAccountIds().size(), request.getUserId());

        // Validate and fetch user
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with ID: " + request.getUserId()));

        List<AccountAssignmentResult> results = new ArrayList<>();
        int successCount = 0;
        int skipCount = 0;
        int failCount = 0;

        // Fetch all requested accounts in a single query (optimization)
        Map<Long, AwsAccount> accountMap = awsAccountRepository
                .findAllById(request.getAccountIds())
                .stream()
                .collect(Collectors.toMap(AwsAccount::getId, account -> account));

        // Get currently assigned account IDs for duplicate checking
        Set<Long> currentlyAssignedIds = user.getAssignedAccounts()
                .stream()
                .map(AwsAccount::getId)
                .collect(Collectors.toSet());

        // Process each account assignment
        for (Long accountId : request.getAccountIds()) {
            try {
                // Check if account exists
                if (!accountMap.containsKey(accountId)) {
                    results.add(createFailedResult(
                            accountId,
                            null,
                            "Account not found"
                    ));
                    failCount++;
                    continue;
                }

                AwsAccount account = accountMap.get(accountId);

                // Check if account is active
                if (!account.getActive()) {
                    results.add(createFailedResult(
                            accountId,
                            account.getAccountAlias(),
                            "Account is inactive"
                    ));
                    failCount++;
                    continue;
                }

                // Check if already assigned
                if (currentlyAssignedIds.contains(accountId)) {
                    results.add(createSkippedResult(
                            accountId,
                            account.getAccountAlias(),
                            "Account already assigned to user"
                    ));
                    skipCount++;
                    continue;
                }

                // Add account to user's assigned accounts (Many-to-Many)
                user.getAssignedAccounts().add(account);

                results.add(createSuccessResult(
                        accountId,
                        account.getAccountAlias(),
                        "Account assigned successfully"
                ));
                successCount++;

            } catch (Exception e) {
                logger.error("Failed to assign account {} to user {}: {}",
                        accountId, request.getUserId(), e.getMessage());

                String accountAlias = accountMap.containsKey(accountId)
                        ? accountMap.get(accountId).getAccountAlias()
                        : "Unknown";

                results.add(createFailedResult(
                        accountId,
                        accountAlias,
                        "Error: " + e.getMessage()
                ));
                failCount++;
            }
        }

        // Save all changes in a single transaction
        userRepository.save(user);

        logger.info("Bulk assignment completed for user {}. Success: {}, Skipped: {}, Failed: {}",
                request.getUserId(), successCount, skipCount, failCount);

        return BulkAccountAssignmentResponse.builder()
                .userId(user.getId())
                .userEmail(user.getEmail())
                .totalAccountsRequested(request.getAccountIds().size())
                .successfulAssignments(successCount)
                .skippedDuplicates(skipCount)
                .failedAssignments(failCount)
                .results(results)
                .build();
    }

//     Remove multiple accounts from a user

    @Transactional
    public BulkAccountAssignmentResponse removeMultipleAccountsFromUser(
            BulkAccountAssignmentToUserRequest request) {

        logger.info("Starting bulk removal: {} accounts from user {}",
                request.getAccountIds().size(), request.getUserId());

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with ID: " + request.getUserId()));

        List<AccountAssignmentResult> results = new ArrayList<>();
        int successCount = 0;
        int skipCount = 0;
        int failCount = 0;

        // Fetch all accounts
        Map<Long, AwsAccount> accountMap = awsAccountRepository
                .findAllById(request.getAccountIds())
                .stream()
                .collect(Collectors.toMap(AwsAccount::getId, account -> account));

        for (Long accountId : request.getAccountIds()) {
            try {
                if (!accountMap.containsKey(accountId)) {
                    results.add(createFailedResult(accountId, null, "Account not found"));
                    failCount++;
                    continue;
                }

                AwsAccount account = accountMap.get(accountId);

                // Remove from user's assigned accounts
                boolean removed = user.getAssignedAccounts().remove(account);

                if (removed) {
                    results.add(createSuccessResult(
                            accountId,
                            account.getAccountAlias(),
                            "Account removed successfully"
                    ));
                    successCount++;
                } else {
                    results.add(createSkippedResult(
                            accountId,
                            account.getAccountAlias(),
                            "Account was not assigned to user"
                    ));
                    skipCount++;
                }

            } catch (Exception e) {
                logger.error("Failed to remove account {} from user {}: {}",
                        accountId, request.getUserId(), e.getMessage());

                String accountAlias = accountMap.containsKey(accountId)
                        ? accountMap.get(accountId).getAccountAlias()
                        : "Unknown";

                results.add(createFailedResult(accountId, accountAlias, "Error: " + e.getMessage()));
                failCount++;
            }
        }

        userRepository.save(user);

        logger.info("Bulk removal completed for user {}. Success: {}, Skipped: {}, Failed: {}",
                request.getUserId(), successCount, skipCount, failCount);

        return BulkAccountAssignmentResponse.builder()
                .userId(user.getId())
                .userEmail(user.getEmail())
                .totalAccountsRequested(request.getAccountIds().size())
                .successfulAssignments(successCount)
                .skippedDuplicates(skipCount)
                .failedAssignments(failCount)
                .results(results)
                .build();
    }

    //     Get all accounts assigned to a user
    @Transactional(readOnly = true)
    public List<AwsAccount> getUserAssignedAccounts(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with ID: " + userId));

        return new ArrayList<>(user.getAssignedAccounts());
    }

    // Helper methods
    private AccountAssignmentResult createSuccessResult(
            Long accountId, String accountAlias, String message) {
        return AccountAssignmentResult.builder()
                .accountId(accountId)
                .accountName(accountAlias)
                .success(true)
                .message(message)
                .build();
    }

    private AccountAssignmentResult createFailedResult(
            Long accountId, String accountAlias, String message) {
        return AccountAssignmentResult.builder()
                .accountId(accountId)
                .accountName(accountAlias)
                .success(false)
                .message(message)
                .build();
    }

    private AccountAssignmentResult createSkippedResult(
            Long accountId, String accountAlias, String message) {
        return AccountAssignmentResult.builder()
                .accountId(accountId)
                .accountName(accountAlias)
                .success(true) // Not a failure, just skipped
                .message(message)
                .build();
    }


}
