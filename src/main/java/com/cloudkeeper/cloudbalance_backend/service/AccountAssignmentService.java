package com.cloudkeeper.cloudbalance_backend.service;

import com.cloudkeeper.cloudbalance_backend.entity.AccountAssignment;
import com.cloudkeeper.cloudbalance_backend.entity.AwsAccount;
import com.cloudkeeper.cloudbalance_backend.entity.User;
import com.cloudkeeper.cloudbalance_backend.exception.ResourceAlreadyExistsException;
import com.cloudkeeper.cloudbalance_backend.exception.ResourceNotFoundException;
import com.cloudkeeper.cloudbalance_backend.repository.jpa.AccountAssignmentRepository;
import com.cloudkeeper.cloudbalance_backend.repository.jpa.AwsAccountRepository;
import com.cloudkeeper.cloudbalance_backend.repository.jpa.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountAssignmentService {

    private final AccountAssignmentRepository accountAssignmentRepository;
    private final AwsAccountRepository awsAccountRepository;
    private final UserRepository userRepository;

    @Transactional
    public void assignAccount(Long awsAccountId, Long userId){
        AwsAccount account = awsAccountRepository.findById(awsAccountId).orElseThrow(() -> new ResourceNotFoundException("Aws account not found."));
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found."));

        if(accountAssignmentRepository.findByAwsAccountIdAndUserId(awsAccountId, userId).isPresent()){
            throw new ResourceAlreadyExistsException("Account already assigned to this user");
        }

        AccountAssignment assignment = AccountAssignment.builder()
                .awsAccount(account)
                .user(user)
                .active(true)
                .build();
        accountAssignmentRepository.save(assignment);
    }

    @Transactional
    public void unassignAccount(Long awsAccountId, Long userId){
        AccountAssignment assignment = accountAssignmentRepository.findByAwsAccountIdAndUserId(awsAccountId, userId).orElseThrow(() -> new ResourceNotFoundException("Assignment not found"));
        assignment.setActive(false);
        accountAssignmentRepository.save(assignment);
    }

}
