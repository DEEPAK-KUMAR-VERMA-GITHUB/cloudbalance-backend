package com.cloudkeeper.cloudbalance_backend.dto.response;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class CostReportResponseDTO {
    private Map<String, DailyCostDataDTO> dailyData;
    private Map<String, MonthlyCostDataDTO> monthlyData;
    private List<GroupWiseDataDTO> groupWiseData;
}
