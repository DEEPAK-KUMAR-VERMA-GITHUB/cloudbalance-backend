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
public class MonthlyCostData {
    private String month; // Format YYYY-MM or "Jan 2026"
    private BigDecimal cost;
    private List<ServiceCostBreakdown> services;
}
