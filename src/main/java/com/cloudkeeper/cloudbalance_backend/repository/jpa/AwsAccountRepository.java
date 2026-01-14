package com.cloudkeeper.cloudbalance_backend.repository.jpa;

import com.cloudkeeper.cloudbalance_backend.entity.AwsAccount;
import com.cloudkeeper.cloudbalance_backend.entity.User;
import io.micrometer.common.KeyValues;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AwsAccountRepository extends JpaRepository<AwsAccount, Long> {
    Optional<AwsAccount> findByAccountId(String accountId);
    List<AwsAccount> findByActiveTrue();

    @Query("SELECT a FROM AwsAccount a JOIN a.assignedUsers u WHERE u.id = :userId")
    List<AwsAccount> findByAssignedUserId(Long userId);
}
