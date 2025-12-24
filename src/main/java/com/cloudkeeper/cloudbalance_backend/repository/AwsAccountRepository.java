package com.cloudkeeper.cloudbalance_backend.repository;

import com.cloudkeeper.cloudbalance_backend.entity.AwsAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AwsAccountRepository extends JpaRepository<AwsAccount, Long> {
    Optional<AwsAccount> findByAccountId(String accountId);

    boolean existsByAccountId(String accountId);

    // Customer's assigned accounts
    @Query("""
                    SELECT a FROM AwsAccount a
                    JOIN AccountAssignment ass
                    ON a.id = ass.awsAccount.id
                    WHERE ass.user.id = :userId
                    AND ass.active = true
                    AND a.active = true
            """)
    List<AwsAccount> findActiveAccountsByUserId(@Param("userId") Long userId);
}
