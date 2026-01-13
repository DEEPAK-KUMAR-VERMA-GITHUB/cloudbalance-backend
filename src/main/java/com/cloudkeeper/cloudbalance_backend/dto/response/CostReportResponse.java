package com.cloudkeeper.cloudbalance_backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostReportResponse {
    private GroupWiseData[] groupWise;
    private LinkedHashMap<String, MonthWiseData> monthWise;
}
