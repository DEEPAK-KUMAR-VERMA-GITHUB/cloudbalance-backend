package com.cloudkeeper.cloudbalance_backend.dto.response;

import lombok.Data;

import java.util.Map;

@Data
public class GroupWiseDataDTO {
    private String groupName;
    private Map<String, Double> periodCostData;
    private double totalCost;
}
