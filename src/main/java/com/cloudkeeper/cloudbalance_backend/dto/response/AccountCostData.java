package com.cloudkeeper.cloudbalance_backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountCostData {
    private String accountId;
    private String accountName;
    private BigDecimal totalCost;
    private List<ServiceCostData> topServices;
}
