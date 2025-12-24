package com.cloudkeeper.cloudbalance_backend.service;

import com.cloudkeeper.cloudbalance_backend.dto.request.AwsAccountCreateRequest;
import com.cloudkeeper.cloudbalance_backend.dto.response.AwsAccountResponse;
import com.cloudkeeper.cloudbalance_backend.entity.AwsAccount;
import com.cloudkeeper.cloudbalance_backend.exception.ResourceAlreadyExistsException;
import com.cloudkeeper.cloudbalance_backend.repository.AwsAccountRepository;
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
    private final TextEncryptor textEncryptor;

    @Transactional
    public AwsAccountResponse createAwsAccount(AwsAccountCreateRequest request) {
        if (awsAccountRepository.existsByAccountId(request.getAccountId())) {
            throw new ResourceAlreadyExistsException("AWS Account ID already exists");
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
