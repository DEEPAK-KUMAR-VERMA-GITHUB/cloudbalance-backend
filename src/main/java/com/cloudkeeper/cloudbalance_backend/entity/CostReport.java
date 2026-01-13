package com.cloudkeeper.cloudbalance_backend.entity;

import jakarta.persistence.Entity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostReport {
    String groupKey;
    Long totalCost;
    Long month;
    Long year;
}
