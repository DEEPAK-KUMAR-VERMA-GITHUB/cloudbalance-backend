package com.cloudkeeper.cloudbalance_backend.dto.request;

import enums.Granularity;
import enums.GroupBy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostReportRequestDTO {
    @NotBlank
    private String startDate;
    @NotBlank
    private String endDate;
    @NotNull
    private String groupBy;
    @NotNull
    private String granularity;
}
