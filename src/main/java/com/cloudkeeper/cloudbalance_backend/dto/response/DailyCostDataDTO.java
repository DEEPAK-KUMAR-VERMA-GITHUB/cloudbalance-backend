package com.cloudkeeper.cloudbalance_backend.dto.response;

import lombok.Data;

import java.util.Map;

@Data
public class DailyCostDataDTO {
    private Map<String, Double> groupData;
    private double totalCost;
}
