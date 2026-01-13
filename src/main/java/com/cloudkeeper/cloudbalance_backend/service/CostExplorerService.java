package com.cloudkeeper.cloudbalance_backend.service;

import com.cloudkeeper.cloudbalance_backend.dto.response.*;
import com.cloudkeeper.cloudbalance_backend.helper.snowflake.SnowflakeUtils;
import com.cloudkeeper.cloudbalance_backend.logging.Logger;
import com.cloudkeeper.cloudbalance_backend.logging.LoggerFactory;
import com.snowflake.snowpark_java.*;
import com.snowflake.snowpark_java.types.DataTypes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CostExplorerService {
    private static final String COST_TABLE = "COSTREPORT";
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private final Session snowparkSession;
    private final Logger logger = LoggerFactory.getLogger(CostExplorerService.class);
    private final SnowflakeUtils snowflakeUtils;


    public CostReportResponse getReports(String groupBy, List<String> accountIds, List<String> filterValues, String startDate, String endDate) {
        if ((accountIds == null || accountIds.isEmpty()) && (filterValues == null || filterValues.isEmpty())) {
            return snowflakeUtils.getDataByGroup(groupBy, startDate, endDate);
        } else if (accountIds == null || accountIds.isEmpty()) {
            return snowflakeUtils.getFilterDataByGroup(groupBy, filterValues, startDate, endDate);
        } else if (filterValues == null || filterValues.isEmpty()) {
            return snowflakeUtils.getDataByGroup(groupBy, accountIds, startDate, endDate);
        } else {
            return snowflakeUtils.getFilterDataByGroup(groupBy, accountIds, filterValues, startDate, endDate);
        }
    }

    public List<String> getFilters(String groupBy, List<String> accountIds) {
        if (accountIds == null || accountIds.isEmpty())
            return snowflakeUtils.getFiltersByGroup(groupBy);
        return snowflakeUtils.getFiltersByGroup(groupBy, accountIds);
    }

    public CostExplorerResponse getCostExplorerData(List<String> accountIds, LocalDate startDate, LocalDate endDate, String groupBy) {
        logger.info("Fetching cost data using Snowpark - Accounts {}, Period : {} to {}", accountIds, startDate, endDate);

        try {
// load base dataframe
            DataFrame df = getFilteredDataFrame(accountIds, startDate, endDate);

            // calculate all matrics
            BigDecimal totalCost = calculateTotalCost(df);
            List<ServiceCostData> serviceCosts = getServiceCosts(df, totalCost);
            List<MonthlyCostData> monthlyCosts = getMonthlyCosts(df);
            List<AccountCostData> accountCosts = getAccountCosts(df);
            List<DailyCostData> dailyCosts = getDailyCosts(df);

            logger.info("Cost data fetched successfully. Total cost : ${}", totalCost);

            return CostExplorerResponse.builder().startDate(startDate).endDate(endDate).totalCost(totalCost).serviceCosts(serviceCosts).monthlyCosts(monthlyCosts).accountCosts(accountCosts).dailyCosts(dailyCosts).build();
        } catch (Exception e) {
            logger.error("Error fetching cost data from Snowflake: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch cost data from Snowflake", e);
        }
    }

    private DataFrame getFilteredDataFrame(List<String> accountIds, LocalDate startDate, LocalDate endDate) {
//        read from snowflake table
        DataFrame df = snowparkSession.table(COST_TABLE);
//        apply date filter
        Column billDateCol = Functions.col("BILL_DATE");
        df = df.filter(billDateCol.geq(Functions.lit(startDate.toString())).and(billDateCol.leq(Functions.lit(endDate.toString()))));
//        apply account filter if specified
        if (accountIds != null && !accountIds.isEmpty()) {
            Column accountIdCol = Functions.col("ACCOUNT_ID");
            df = df.filter(accountIdCol.in(accountIds.toArray()));
            logger.debug("Applied account filter for {} accounts", accountIds.size());
        }
        return df;
    }

    private BigDecimal calculateTotalCost(DataFrame df) {

        DataFrame totalDf =
                df.select(
                        Functions.sum(Functions.col("COST"))
                                .cast(DataTypes.createDecimalType(38, 2))
                                .as("TOTAL_COST")
                );

        Row[] rows = totalDf.collect();

        if (rows.length > 0 && !rows[0].isNullAt(0)) {
            return rows[0].getDecimal(0); // ✅ SAFE NOW
        }

        return BigDecimal.ZERO;
    }


    private List<ServiceCostData> getServiceCosts(DataFrame df, BigDecimal totalCost) {
        logger.debug("Calculating service costs...");
        DataFrame serviceDf = df.groupBy(Functions.col("SERVICE")).agg(Functions.sum(Functions.col("COST")).cast(DataTypes.createDecimalType(38, 2)).as("SERVICE_COST")).sort(Functions.col("SERVICE_COST").desc());

        Row[] rows = serviceDf.collect();
        List<ServiceCostData> serviceCosts = new ArrayList<>();

        for (Row row : rows) {
            if (row.isNullAt(0) || row.isNullAt(1)) continue;

            String serviceName = row.getString(0);
            BigDecimal cost = row.getDecimal(1);

            Double percentage = totalCost.compareTo(BigDecimal.ZERO) > 0 ? cost.divide(totalCost, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue() : 0.0;

            serviceCosts.add(ServiceCostData.builder().serviceName(serviceName).cost(cost).percentage(percentage).build());

        }

        logger.debug("Found {} services", serviceCosts.size());
        return serviceCosts;

    }

    private List<MonthlyCostData> getMonthlyCosts(DataFrame df) {
        logger.debug("Calculating monthly costs...");
        // extract month from BILL_DATE
        DataFrame monthlyDf = df.withColumn("MONTH", Functions.date_format(Functions.col("BILL_DATE"), "yyyy-MM")).groupBy(Functions.col("MONTH"), Functions.col("SERVICE")).agg(Functions.sum(Functions.col("COST")).as("MONTHLY_COST")).sort(Functions.col("MONTH"), Functions.col("MONTHLY_COST").desc());

        Row[] rows = monthlyDf.collect();

//        Map by month
        Map<String, List<ServiceCostBreakdown>> monthlyMap = new LinkedHashMap<>();

        for (Row row : rows) {
            if (row.isNullAt(0) || row.isNullAt(1) || row.isNullAt(3)) continue;

            String month = row.getString(0);
            String service = row.getString(1);
            BigDecimal cost = row.getDecimal(2);

            monthlyMap.computeIfAbsent(month, k -> new ArrayList<>()).add(ServiceCostBreakdown.builder().serviceName(service).cost(cost).build());

        }

        // Convert to MonthlyCostData list
        List<MonthlyCostData> monthlyCosts = monthlyMap.entrySet().stream().map(entry -> {
            BigDecimal monthTotal = entry.getValue().stream().map(ServiceCostBreakdown::getCost).reduce(BigDecimal.ZERO, BigDecimal::add);

            return MonthlyCostData.builder().month(entry.getKey()).cost(monthTotal).services(entry.getValue()).build();
        }).collect(Collectors.toList());

        logger.debug("Found {} months of data", monthlyCosts.size());
        return monthlyCosts;

    }

    private List<AccountCostData> getAccountCosts(DataFrame df) {
        logger.debug("Calculating account costs...");

        DataFrame accountDf = df.groupBy(Functions.col("ACCOUNT_ID"), Functions.col("SERVICE")).agg(Functions.sum(Functions.col("COST")).as("ACCOUNT_COST")).sort(Functions.col("ACCOUNT_ID"), Functions.col("ACCOUNT_COST").desc());

        Row[] rows = accountDf.collect();

        // Group by account
        Map<String, List<ServiceCostData>> accountMap = new LinkedHashMap<>();

        for (Row row : rows) {
            if (row.isNullAt(0) || row.isNullAt(1) || row.isNullAt(2)) continue;

            String accountId = row.getString(0);
            String service = row.getString(1);
            BigDecimal cost = row.getDecimal(2);

            accountMap.computeIfAbsent(accountId, k -> new ArrayList<>()).add(ServiceCostData.builder().serviceName(service).cost(cost).build());
        }

        // Convert to AccountCostData list
        List<AccountCostData> accountCosts = accountMap.entrySet().stream().map(entry -> {
            String accountId = String.valueOf(entry.getKey());
            List<ServiceCostData> allServices = entry.getValue();

            BigDecimal totalAccountCost = allServices.stream().map(ServiceCostData::getCost).reduce(BigDecimal.ZERO, BigDecimal::add);

            // Get top 5 services
            List<ServiceCostData> topServices = allServices.stream().limit(5).collect(Collectors.toList());

            return AccountCostData.builder().accountId(accountId).accountName("AWS Account " + accountId).totalCost(totalAccountCost).topServices(topServices).build();
        }).collect(Collectors.toList());

        logger.debug("Found {} accounts", accountCosts.size());
        return accountCosts;
    }


    private List<DailyCostData> getDailyCosts(DataFrame df) {
        logger.debug("Calculating daily costs...");

        DataFrame dailyDf = df.groupBy(Functions.col("BILL_DATE")).agg(Functions.sum(Functions.col("COST")).as("DAILY_COST")).sort(Functions.col("BILL_DATE"));

        Row[] rows = dailyDf.collect();
        List<DailyCostData> dailyCosts = new ArrayList<>();

        for (Row row : rows) {
            if (row.isNullAt(0) || row.isNullAt(1)) continue;

            LocalDate date = row.getDate(0).toLocalDate();
            BigDecimal cost = row.getDecimal(1);

            dailyCosts.add(DailyCostData.builder().date(date).cost(cost).build());
        }

        logger.debug("Found {} days of data", dailyCosts.size());
        return dailyCosts;
    }


    public List<Map<String, Object>> getGroupedCostData(List<String> accountIds, LocalDate startDate, LocalDate endDate, String groupByColumn) {
        logger.info("Fetching grouped cost data by: {}", groupByColumn);

        try {
            DataFrame df = getFilteredDataFrame(accountIds, startDate, endDate);

            // Group by specified column
            DataFrame groupedDf = df.groupBy(Functions.col(groupByColumn)).agg(Functions.sum(Functions.col("COST")).as("TOTAL_COST")).sort(Functions.col("TOTAL_COST").desc());

            Row[] rows = groupedDf.collect();
            List<Map<String, Object>> result = new ArrayList<>();

            for (Row row : rows) {
                if (row.isNullAt(0) || row.isNullAt(1)) continue;

                Map<String, Object> item = new HashMap<>();
                item.put("name", row.get(0));
                item.put("cost", row.getDecimal(1));
                result.add(item);
            }

            logger.debug("Found {} groups for column: {}", result.size(), groupByColumn);
            return result;

        } catch (Exception e) {
            logger.error("Error fetching grouped cost data: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch grouped cost data", e);
        }
    }

    public boolean testConnection() {
        try {
            DataFrame df = snowparkSession.sql("SELECT CURRENT_VERSION()");
            Row[] rows = df.collect();
            String version = rows[0].getString(0);
            logger.info("✓ Snowflake connection successful. Version: {}", version);
            return true;
        } catch (Exception e) {
            logger.error("✗ Snowflake connection failed: {}", e.getMessage());
            return false;
        }
    }


}
