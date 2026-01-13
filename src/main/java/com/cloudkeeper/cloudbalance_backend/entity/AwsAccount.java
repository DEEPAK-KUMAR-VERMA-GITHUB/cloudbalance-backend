package com.cloudkeeper.cloudbalance_backend.entity;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "aws_accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AwsAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String accountId; // aws account id 12 digits
    @Column(nullable = false)
    private String accountAlias;
    @Column(name = "role_arn")
    private String roleArn; // IAM Role ARN for access
    @Column(name = "external_account_id")
    private String externalId;

    @ManyToMany(mappedBy = "assignedAccounts")
    @Builder.Default
    private Set<User> assignedUsers = new HashSet<>();

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    @CreationTimestamp
    private LocalDateTime createdAt;
    @UpdateTimestamp
    private LocalDateTime updateAt;
}
