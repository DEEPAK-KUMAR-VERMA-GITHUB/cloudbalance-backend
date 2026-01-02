package com.cloudkeeper.cloudbalance_backend.entity;


import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
    private String accountId;
    @Column(nullable = false)
    private String accountAlias;
    @Column(nullable = false, length = 100)
    private String accessKeyId;
    @Column(nullable = false, length = 100)
    private String secretAccessKey;
    @Column(nullable = false)
    private String region;
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;
    @Column(precision = 12, scale = 2)
    private BigDecimal monthlyBudget;
    @CreationTimestamp
    private LocalDateTime createdAt;
    @UpdateTimestamp
    private LocalDateTime updateAt;

    @OneToMany(mappedBy = "awsAccount", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore
    @Builder.Default
    private List<AccountAssignment> assignments = new ArrayList<>();

    // helper methods
    public void addAssignment(AccountAssignment assignment){
        assignments.add(assignment);
        assignment.setAwsAccount(this);
    }

    public void removeAssignment(AccountAssignment assignment){
        assignments.remove(assignment);
        assignment.setAwsAccount(null);
    }

    // ensure that account is active
    @PrePersist
    @PreUpdate
    public void ensureActive(){
        if(this.getActive() == null){
            this.setActive(Boolean.TRUE);
        }
    }
}
