package com.cloudkeeper.cloudbalance_backend.service;

import com.cloudkeeper.cloudbalance_backend.entity.AwsAccount;
import com.cloudkeeper.cloudbalance_backend.entity.User;
import com.cloudkeeper.cloudbalance_backend.entity.UserRole;
import com.cloudkeeper.cloudbalance_backend.exception.ResourceNotFoundException;
import com.cloudkeeper.cloudbalance_backend.logging.Logger;
import com.cloudkeeper.cloudbalance_backend.logging.LoggerFactory;
import com.cloudkeeper.cloudbalance_backend.repository.jpa.AwsAccountRepository;
import com.cloudkeeper.cloudbalance_backend.repository.jpa.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountAssignmentService {

    private final AwsAccountRepository awsAccountRepository;
    private final UserRepository userRepository;
    private final Logger logger = LoggerFactory.getLogger(AccountAssignmentService.class);

    @Transactional
    public void assignAccount(Long awsAccountId, Long userId) {
        AwsAccount account = awsAccountRepository.findById(awsAccountId).orElseThrow(() -> new ResourceNotFoundException("Aws account not found."));
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found."));

        if (!user.getRole().equals(UserRole.CUSTOMER)) {
            throw new IllegalArgumentException("User is not a customer.");
        }

        user.getAssignedAccounts().add(account);
        userRepository.save(user);
        logger.info("Account {} assigned to user {}", account.getAccountAlias(), user.getEmail());
    }

    @Transactional
    public void unassignAccount(Long awsAccountId, Long userId) {
        AwsAccount account = awsAccountRepository.findById(awsAccountId).orElseThrow(() -> new ResourceNotFoundException("AWS Account not found."));
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!user.getRole().equals(UserRole.CUSTOMER)) {
            throw new IllegalArgumentException("User is not a customer.");
        }

        user.getAssignedAccounts().remove(account);
        userRepository.save(user);
        logger.info("Account {} unassigned from user {}", account.getAccountAlias(), user.getEmail());
    }
}
