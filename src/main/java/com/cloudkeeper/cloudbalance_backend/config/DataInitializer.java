package com.cloudkeeper.cloudbalance_backend.config;

import com.cloudkeeper.cloudbalance_backend.entity.AwsAccount;
import com.cloudkeeper.cloudbalance_backend.entity.User;
import com.cloudkeeper.cloudbalance_backend.entity.UserRole;
import com.cloudkeeper.cloudbalance_backend.repository.jpa.AwsAccountRepository;
import com.cloudkeeper.cloudbalance_backend.repository.jpa.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final AwsAccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final Random random = new Random();

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {

            return;
        }


        // Create Admin User
        User admin = createUser(
                "Admin", "User", "admin@cloudbalance.com",
                "Admin123!", UserRole.ADMIN
        );

        // Create Customer Users
        User customer1 = createUser(
                "John", "Doe", "john.doe@company.com",
                "Customer123!", UserRole.CUSTOMER
        );

        User customer2 = createUser(
                "Jane", "Smith", "jane.smith@company.com",
                "Customer123!", UserRole.CUSTOMER
        );

        // Create Read-Only User
        User readonly = createUser(
                "Read", "Only", "readonly@cloudbalance.com",
                "Readonly123!", UserRole.READ_ONLY
        );

        // Create AWS Accounts
        AwsAccount prodAccount = createAwsAccount(
                "123456789012",
                "Production Account",
                "arn:aws:iam::123456789012:role/CloudBalanceRole"
        );

        AwsAccount devAccount = createAwsAccount(
                "210987654321",
                "Development Account",
                "arn:aws:iam::210987654321:role/CloudBalanceRole"
        );

        AwsAccount stagingAccount = createAwsAccount(
                "345678901234",
                "Staging Account",
                "arn:aws:iam::555666777888:role/CloudBalanceRole"
        );

        // Assign accounts to customers
        assignAccountToUser(customer1, Set.of(prodAccount, devAccount));
        assignAccountToUser(customer2, Set.of(stagingAccount));


    }

    private User createUser(String firstName, String lastName, String email,
                            String password, UserRole role) {
        User user = User.builder()
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .password(passwordEncoder.encode(password))
                .role(role)
                .active(true)
                .build();
        return userRepository.save(user);
    }

    private AwsAccount createAwsAccount(String accountId, String accountName, String roleArn) {
        AwsAccount account = AwsAccount.builder()
                .accountId(accountId)
                .accountAlias(accountName)
                .roleArn(roleArn)
                .externalId("external-id-" + accountId)
                .active(true)
                .build();
        return accountRepository.save(account);
    }

    private void assignAccountToUser(User user, Set<AwsAccount> accounts) {
        user.setAssignedAccounts(accounts);
        userRepository.save(user);
    }

}
