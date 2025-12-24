package com.cloudkeeper.cloudbalance_backend.repository;

import com.cloudkeeper.cloudbalance_backend.entity.AccountAssignment;
import com.cloudkeeper.cloudbalance_backend.entity.AwsAccount;
import com.cloudkeeper.cloudbalance_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountAssignmentRepository extends JpaRepository<AccountAssignment, Long> {
    List<AccountAssignment> findByAwsAccountAndActiveTrue(AwsAccount awsAccount);
    List<AccountAssignment> findByUserAndActiveTrue(User user);

    Optional<AccountAssignment> findByAwsAccountIdAndUserId(Long awsAccountId, Long userId);

    @Query("SELECT ass FROM AccountAssignment ass WHERE ass.awsAccount.accountId = :accountId")
    List<AccountAssignment> findByAccountId(@Param("accountId") String accountId);
}
