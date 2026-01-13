package com.cloudkeeper.cloudbalance_backend.helper.snowflake;

import com.cloudkeeper.cloudbalance_backend.dto.response.CostReportResponse;
import com.cloudkeeper.cloudbalance_backend.dto.response.GroupWiseData;
import com.cloudkeeper.cloudbalance_backend.dto.response.MonthWiseData;
import com.cloudkeeper.cloudbalance_backend.entity.CostReport;
import com.snowflake.snowpark_java.Row;
import com.snowflake.snowpark_java.Session;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

import java.util.*;
import java.util.stream.Collectors;

@Configuration
@RequiredArgsConstructor
public class SnowflakeUtils {

    private static final String TOTAL_COST = "total_cost";
    private static final String BILL_MONTH = "bill_month";
    private static final String BILL_YEAR = "bill_year";
    private final Session snowFlakeSession;

    public List<String> getFiltersByGroup(String groupBy) {
        Row[] rows = snowFlakeSession.sql(String.format("SELECT %s FROM COSTREPORT GROUP BY %s", groupBy, groupBy)).collect();
        List<String> values = new ArrayList<>();
        for (Row row : rows) {
            values.add(row.getAs(groupBy, String.class));
        }
        return values;
    }

    public List<String> getFiltersByGroup(String groupBy, List<String> accountIds) {
        Row[] rows = snowFlakeSession.sql(String.format("SELECT %s FROM COSTREPORT WHERE account_id IN (%s) GROUP BY %s", groupBy, String.join(",", accountIds), groupBy)).collect();
        List<String> values = new ArrayList<>();
        for (Row row : rows) {
            values.add(row.getAs(groupBy, String.class));
        }
        return values;
    }

    public CostReportResponse getFilterDataByGroup(String groupBy, List<String> groupByValues, String startDate, String endDate) {
        Row[] rows = snowFlakeSession.sql(String.format("""
                SELECT %s,YEAR(bill_date)  AS bill_year,MONTH(bill_date) AS bill_month,SUM(cost)\s
                AS total_cost FROM costreport   WHERE %s in ('%s') AND bill_date >= COALESCE(%s, '1900-01-01'::DATE)
                  AND bill_date <= COALESCE(%s, CURRENT_DATE()) GROUP BY %s,
                YEAR(bill_date),MONTH(bill_date) ORDER BY %s,bill_year,bill_month
               \s""", groupBy, groupBy, String.join("','", groupByValues), startDate == null ? null : "'" + startDate + "'", endDate == null ? null : "'" + endDate + "'", groupBy, groupBy)).collect();
        List<CostReport> reports = new ArrayList<>();
        for (Row row : rows) {
            CostReport costReport = new CostReport(row.getAs(groupBy, String.class), row.getAs(TOTAL_COST, Long.class), row.getAs(BILL_MONTH, Long.class), row.getAs(BILL_YEAR, Long.class));
            reports.add(costReport);
        }

        return mapToCostReportResponse(reports);
    }

    public CostReportResponse getFilterDataByGroup(String groupBy, List<String> accountIds, List<String> groupByValues, String startDate, String endDate) {
        Row[] rows = snowFlakeSession.sql(String.format("""
                 SELECT %s, YEAR(bill_date) AS Bill_Year, MONTH(bill_date) AS Bill_Month, SUM(cost) AS Total_Cost
                 FROM COSTREPORT
                 WHERE %s IN ('%s')\s
                     AND account_id in (%s)\s
                     AND bill_date >= COALESCE(%s, '1900-01-01'::DATE)
                     AND bill_date <= COALESCE(%s, CURRENT_DATE())
                 GROUP BY %s, YEAR(bill_date), MONTH(bill_date)
                 ORDER BY %s, bill_year, bill_month
                \s""", groupBy, groupBy, String.join(",", groupByValues), String.join(",", accountIds), startDate == null ? null : "'" + startDate + "'", endDate == null ? null : "'" + endDate + "'", groupBy, groupBy)).collect();
        List<CostReport> reports = new ArrayList<>();
        for (Row row : rows) {
            reports.add(CostReport.builder().groupKey(row.getAs(groupBy, String.class)).totalCost(row.getAs(TOTAL_COST, Long.class)).month(row.getAs(BILL_MONTH, Long.class)).year(row.getAs(BILL_YEAR, Long.class)).build());
        }
        return mapToCostReportResponse(reports);
    }

    public CostReportResponse getDataByGroup(String groupBy, List<String> accountIds, String startDate, String endDate) {
        Row[] rows = snowFlakeSession.sql(String.format("""
                 SELECT %s,YEAR(bill_date)  AS bill_year,MONTH(bill_date) AS bill_month,SUM(cost)
                 AS total_cost FROM costreport where account_id in (%s) AND bill_date >= COALESCE(%s, '1900-01-01'::DATE)
                   AND bill_date <= COALESCE(%s, CURRENT_DATE()) GROUP BY %s,
                 YEAR(bill_date),MONTH(bill_date) ORDER BY %s,bill_year,bill_month
                """, groupBy, String.join(",", accountIds), startDate == null ? null : "'" + startDate + "'", endDate == null ? null : "'" + endDate + "'", groupBy, groupBy)).collect();
        List<CostReport> reports = new ArrayList<>();
        for (Row row : rows) {
            CostReport costReport = new CostReport(row.getAs(groupBy, String.class), row.getAs(TOTAL_COST, Long.class), row.getAs(BILL_MONTH, Long.class), row.getAs(BILL_YEAR, Long.class));
            reports.add(costReport);
        }

        return mapToCostReportResponse(reports);
    }

    public CostReportResponse getDataByGroup(String groupBy, String startDate, String endDate) {
        Row[] rows = snowFlakeSession.sql(String.format("""
                SELECT
                    %s,
                    YEAR(bill_date)  AS bill_year,
                    MONTH(bill_date) AS bill_month,
                    SUM(cost) AS total_cost
                FROM costreport
                WHERE bill_date >= COALESCE(%s, '1900-01-01'::DATE)
                  AND bill_date <= COALESCE(%s, CURRENT_DATE())
                GROUP BY %s, YEAR(bill_date), MONTH(bill_date)
                ORDER BY %s, bill_year, bill_month;
                """, groupBy, startDate == null ? null : "'" + startDate + "'", endDate == null ? null : "'" + endDate + "'", groupBy, groupBy)).collect();
        List<CostReport> reports = new ArrayList<>();
        for (Row row : rows) {
            CostReport costReport = new CostReport(row.getAs(groupBy, String.class), row.getAs(TOTAL_COST, Long.class), row.getAs(BILL_MONTH, Long.class), row.getAs(BILL_YEAR, Long.class));
            reports.add(costReport);
        }

        return mapToCostReportResponse(reports);
    }

    private CostReportResponse mapToCostReportResponse(List<CostReport> reports) {

        GroupWiseData[] groupWiseData = reports.stream().collect(Collectors.groupingBy(CostReport::getGroupKey)).entrySet().stream().map(e -> {
            List<CostReport> list = e.getValue();
            Map<String, Long> monthlyData = list.stream().collect(Collectors.groupingBy(CostReport::getMonth, Collectors.groupingBy(CostReport::getYear))).entrySet().stream().flatMap(monthEntry -> monthEntry.getValue().entrySet().stream().map(me -> {
                String key = monthEntry.getKey() + "/" + me.getKey();
                Long value = me.getValue().stream().mapToLong(CostReport::getTotalCost).sum();
                return Map.entry(key, value);
            })).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            Long totalCost = list.stream().mapToLong(CostReport::getTotalCost).sum();
            return GroupWiseData.builder().groupName(e.getKey()).monthlyData(monthlyData).totalCost(totalCost).build();
        }).toArray(GroupWiseData[]::new);

        LinkedHashMap<String, MonthWiseData> monthWiseData = reports.stream().collect(Collectors.groupingBy(CostReport::getMonth, Collectors.groupingBy(CostReport::getYear))).entrySet().stream().flatMap(monthEntry -> monthEntry.getValue().entrySet().stream().map(yearEntry -> {
            List<CostReport> list = yearEntry.getValue();
            String key = monthEntry.getKey() + "/" + yearEntry.getKey();
            Map<String, Long> groupData = list.stream().map(cr -> Map.entry(cr.getGroupKey(), cr.getTotalCost())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            Long totalCost = list.stream().mapToLong(CostReport::getTotalCost).sum();

            return Map.entry(key, MonthWiseData.builder().groupData(groupData).totalCost(totalCost).build());
        })).sorted(Comparator.comparing((Map.Entry<String, MonthWiseData> e) -> {
            String[] parts = e.getKey().split("/");
            int month = Integer.parseInt(parts[0]);
            int year = Integer.parseInt(parts[1]);
            return year * 100 + month;
        })).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));

        return CostReportResponse.builder().groupWise(groupWiseData).monthWise(monthWiseData).build();

    }

}
