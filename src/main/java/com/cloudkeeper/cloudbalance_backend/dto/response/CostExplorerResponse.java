package com.cloudkeeper.cloudbalance_backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostExplorerResponse {
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal totalCost;
    private List<ServiceCostData> serviceCosts;
    private List<MonthlyCostData> monthlyCosts;
    private List<AccountCostData> accountCosts;
    private List<DailyCostData> dailyCosts;
}
