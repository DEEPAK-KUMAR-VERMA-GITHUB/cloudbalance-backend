package com.cloudkeeper.cloudbalance_backend.service;

import com.cloudkeeper.cloudbalance_backend.dto.request.CostReportRequestDTO;
import com.cloudkeeper.cloudbalance_backend.dto.response.CostReportResponseDTO;
import com.cloudkeeper.cloudbalance_backend.dto.response.DailyCostDataDTO;
import com.cloudkeeper.cloudbalance_backend.dto.response.GroupWiseDataDTO;
import com.cloudkeeper.cloudbalance_backend.dto.response.MonthlyCostDataDTO;
import com.cloudkeeper.cloudbalance_backend.helper.snowflake.SnowflakeUtils;
import com.cloudkeeper.cloudbalance_backend.repository.snowflake.SnowflakeRepository;
import com.snowflake.snowpark_java.Row;
import enums.Granularity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CostExplorerService {
    private final SnowflakeRepository snowflakeRepo;

    public CostReportResponseDTO getCostReport(CostReportRequestDTO request, List<String> accountIds) {

        Granularity gran = Granularity.valueOf(request.getGranularity().toUpperCase());

        // build account filter
        String accountFilterSql = getAccountFilterSql(accountIds);

        // build period expression
        String periodExpr = SnowflakeUtils.getPeriodExpression(gran);

        // SQL queries
        String monthlySql = buildMonthlyQuery(request, periodExpr, accountFilterSql);
        String groupSql = buildGroupWiseQuery(request, periodExpr, accountFilterSql);

        // run against Snowflake
        List<Row> monthlyRows = snowflakeRepo.executeQuery(monthlySql);
        List<Row> groupRows = snowflakeRepo.executeQuery(groupSql);

        return mapToResponse(gran, monthlyRows, groupRows);
    }

    private String getAccountFilterSql(List<String> accountIds) {

        String inClause = accountIds.stream().map(id -> "'" + id + "'").collect(Collectors.joining(", "));

        return "AND account_id IN (" + inClause + ")";
    }

    private String buildMonthlyQuery(CostReportRequestDTO req, String periodExpr, String accountFilter) {
        return String.format("SELECT %s AS period, %s AS group_key, ROUND(SUM(cost), 2) AS cost " + "FROM AWS_COST_USAGE_FACT " + "WHERE usage_date BETWEEN '%s' AND '%s' %s " + "GROUP BY %s, %s ORDER BY %s, cost DESC", periodExpr, req.getGroupBy(), req.getStartDate(), req.getEndDate(), accountFilter, periodExpr, req.getGroupBy(), periodExpr);
    }

    private String buildGroupWiseQuery(CostReportRequestDTO req, String periodExpr, String accountFilter) {
        return String.format("SELECT %s AS group_key, %s AS period, ROUND(SUM(cost), 2) AS total_cost " + "FROM AWS_COST_USAGE_FACT " + "WHERE usage_date BETWEEN '%s' AND '%s' %s " + "GROUP BY %s, %s ORDER BY total_cost DESC", req.getGroupBy(), periodExpr, req.getStartDate(), req.getEndDate(), accountFilter, req.getGroupBy(), periodExpr);
    }

    private CostReportResponseDTO mapToResponse(Granularity gran, List<Row> monthlyRows, List<Row> groupRows) {

        Map<String, DailyCostDataDTO> daily = new TreeMap<>();
        Map<String, MonthlyCostDataDTO> monthly = new TreeMap<>();

        for (Row r : monthlyRows) {
            String period = r.getAs("PERIOD", String.class);
            String key = r.getAs("GROUP_KEY", String.class);
            BigDecimal bdCost = r.getAs("COST", BigDecimal.class);
            double cost = bdCost != null ? bdCost.doubleValue() : 0.0;

            if (gran == Granularity.DAILY) {
                daily.computeIfAbsent(period, p -> {
                    DailyCostDataDTO dto = new DailyCostDataDTO();
                    dto.setGroupData(new HashMap<>());
                    return dto;
                }).getGroupData().put(key, cost);
            } else {
                monthly.computeIfAbsent(period, p -> {
                    MonthlyCostDataDTO dto = new MonthlyCostDataDTO();
                    dto.setGroupData(new HashMap<>());
                    return dto;
                }).getGroupData().put(key, cost);
            }
        }

        // post-aggregate totals
        if (gran == Granularity.DAILY) {
            daily.forEach((k, v) -> v.setTotalCost(v.getGroupData().values().stream().mapToDouble(Double::doubleValue).sum()));
        } else {
            monthly.forEach((k, v) -> v.setTotalCost(v.getGroupData().values().stream().mapToDouble(Double::doubleValue).sum()));
        }

        List<GroupWiseDataDTO> groupDataList = new ArrayList<>();

        for (Row r : groupRows) {
            String name = r.getAs("GROUP_KEY", String.class);
            String period = r.getAs("PERIOD", String.class);

            BigDecimal bdTotal = r.getAs("TOTAL_COST", BigDecimal.class);
            double total = bdTotal != null ? bdTotal.doubleValue() : 0.0;

            GroupWiseDataDTO dto = new GroupWiseDataDTO();
            dto.setGroupName(name);

            Map<String, Double> map = new HashMap<>();
            map.put(period, total);
            dto.setPeriodCostData(map);

            dto.setTotalCost(total);
            groupDataList.add(dto);
        }

        CostReportResponseDTO response = new CostReportResponseDTO();
        response.setDailyData(daily);
        response.setMonthlyData(monthly);
        response.setGroupWiseData(groupDataList);

        return response;
    }

}
