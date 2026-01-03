package com.cloudkeeper.cloudbalance_backend.repository.jpa;

import com.cloudkeeper.cloudbalance_backend.entity.AwsAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AwsAccountRepository extends JpaRepository<AwsAccount, Long> {

//    Check if account exists by AWS Account ID
    boolean existsByAccountId(String accountId);

//    Find account by AWS Account ID
    Optional<AwsAccount> findByAccountId(String accountId);

//    Find active accounts assigned to a specific user
    @Query("SELECT a FROM AwsAccount a JOIN a.assignments asg WHERE asg.user.id = :userId AND a.active = true AND asg.active = true")
    List<AwsAccount> findActiveAccountsByUserId(@Param("userId") Long userId);

//    Find all accounts assigned to a user (active or inactive)
    @Query("SELECT a FROM AwsAccount a JOIN a.assignments asg WHERE asg.user.id = :userId")
    List<AwsAccount> findAllAccountsByUserId(@Param("userId") Long userId);

//    Find all active accounts
    List<AwsAccount> findByActiveTrue();

//    Find accounts by region
    List<AwsAccount> findByRegion(String region);

//    Find accounts by alias (case-insensitive)
    Optional<AwsAccount> findByAccountAliasIgnoreCase(String accountAlias);
}
