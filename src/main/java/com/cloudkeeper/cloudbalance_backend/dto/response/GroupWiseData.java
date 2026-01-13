package com.cloudkeeper.cloudbalance_backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupWiseData {
    private String groupName;
    private Map<String, Long> monthlyData;
    private Long totalCost;
}
