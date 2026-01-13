package com.cloudkeeper.cloudbalance_backend.controller;

import com.cloudkeeper.cloudbalance_backend.config.UserPrincipal;
import com.cloudkeeper.cloudbalance_backend.dto.response.ApiResponse;
import com.cloudkeeper.cloudbalance_backend.dto.response.AwsAccountResponse;
import com.cloudkeeper.cloudbalance_backend.dto.response.CostExplorerResponse;
import com.cloudkeeper.cloudbalance_backend.dto.response.CostReportResponse;
import com.cloudkeeper.cloudbalance_backend.entity.AwsAccount;
import com.cloudkeeper.cloudbalance_backend.entity.UserRole;
import com.cloudkeeper.cloudbalance_backend.helper.roleAnnotations.AnyAuthenticatedUser;
import com.cloudkeeper.cloudbalance_backend.helper.roleAnnotations.ReadOnlyOrAbove;
import com.cloudkeeper.cloudbalance_backend.logging.Logger;
import com.cloudkeeper.cloudbalance_backend.logging.LoggerFactory;
import com.cloudkeeper.cloudbalance_backend.service.AwsAccountService;
import com.cloudkeeper.cloudbalance_backend.service.CostExplorerService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/cost-explorer")
@RequiredArgsConstructor
public class CostExplorerController {
    private final CostExplorerService costExplorerService;
    private final AwsAccountService awsAccountService;
    private final Logger logger = LoggerFactory.getLogger(CostExplorerController.class);


    @GetMapping("/reports")
    @ReadOnlyOrAbove
    public ResponseEntity<ApiResponse<CostReportResponse>> getAllReports(@RequestParam(defaultValue = "service", name = "group_by") String groupBy, @RequestParam(name = "accountIds", required = false) List<@Pattern(regexp = "^\\d{12}$", message = "Invalid AWS accountId") String> accountIds, @RequestParam(name = "filters", required = false) List<String> filters, @RequestParam(name = "start_date", required = false) @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])$", message = "Date must be in YYYY-MM-DD format") String startDate, @RequestParam(name = "end_date", required = false) @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])$", message = "Date must be in YYYY-MM-DD format") String endDate) {
        return ResponseEntity.ok(ApiResponse.<CostReportResponse>builder().success(true).message("Reports fetched successfully").data(costExplorerService.getReports(groupBy, accountIds, filters, startDate, endDate)).build());
    }

    @GetMapping("/filters")
    @ReadOnlyOrAbove
    public ResponseEntity<ApiResponse<List<String>>> getFilters(@RequestParam(defaultValue = "service", name = "group_by") String groupBy, @RequestParam(name = "accountIds", required = false) List<@Pattern(regexp = "^\\d{12}$", message = "Invalid AWS accountId") String> accountIds) {
        return ResponseEntity.ok(ApiResponse.<List<String>>builder().success(true).message("Fetched filters successfully").data(costExplorerService.getFilters(groupBy, accountIds)).build());
    }

    @GetMapping("/data")
    @AnyAuthenticatedUser
    @Operation(summary = "Get cost explorer data", description = "Fetch AWS cost data from Snowflake based on user role and account access")
    public ResponseEntity<ApiResponse<CostExplorerResponse>> getCostData(@AuthenticationPrincipal UserPrincipal userPrincipal, @RequestParam(required = false) List<String> accountIds, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate, @RequestParam(defaultValue = "SERVICE") String groupBy) {

        logger.info("Fetching cost data for user : {}, role: {}", userPrincipal.getUsername(), userPrincipal.getAuthorities());

        try {

//            get accessible account IDs based on user role
            List<String> accessibleAccountIds = getAccessibleAccountIds(userPrincipal, accountIds);

            if (accessibleAccountIds.isEmpty()) {
                return ResponseEntity.ok(ApiResponse.<CostExplorerResponse>builder().success(false).message("No accessible accounts found for this user.").build());
            }

            logger.info("User has access to {} accounts", accessibleAccountIds.size());

//            fetch cost data from snowflake using snowpark
            CostExplorerResponse costData = costExplorerService.getCostExplorerData(accessibleAccountIds, startDate, endDate, groupBy);

            return ResponseEntity.ok(ApiResponse.<CostExplorerResponse>builder().success(true).message("Cost data retrieved successfully from Snowflake").data(costData).build());

        } catch (Exception e) {
            logger.error("Error fetching cost data : {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.<CostExplorerResponse>builder().success(false).message("Failed to fetch cost data : " + e.getMessage()).build());
        }
    }

    @GetMapping("/test-connection")
    @AnyAuthenticatedUser
    @Operation(summary = "Test Snowflake connection", description = "Admin only - test Snowflake connectivity")
    public ResponseEntity<ApiResponse<String>> testConnection() {
        logger.info("Testing Snowflake connection...");

        try {
            boolean connected = costExplorerService.testConnection();

            if (connected) {
                return ResponseEntity.ok(ApiResponse.<String>builder().success(true).message("Snowflake connection successful").data("Connected to Snowflake via Snowpark").build());
            } else {
                return ResponseEntity.ok(ApiResponse.<String>builder().success(false).message("Snowflake connection failed").build());
            }
        } catch (Exception e) {
            logger.error("Connection test failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.<String>builder().success(false).message("Connection test failed: " + e.getMessage()).build());
        }
    }

    private List<String> getAccessibleAccountIds(UserPrincipal userPrincipal, List<String> accountIds) {
        String role = userPrincipal.getAuthorities().stream().findFirst().map(GrantedAuthority::getAuthority).orElse("");
        if (!role.isEmpty()) {
            role = role.split("_")[1];
        }
        logger.debug("Determining accessible accounts for role : {}", role);

        if (UserRole.ADMIN.getDisplayName().equalsIgnoreCase(role) || UserRole.READ_ONLY.getDisplayName().equalsIgnoreCase(role)) {
            // Admin and readonly can access all accounts
            if (accountIds != null && !accountIds.isEmpty()) {
                logger.debug("Admin/ReadOnly requesting specific accounts : {}", accountIds);
                return accountIds;
            }

            // return all account ids from db
            List<String> allAccountIds = awsAccountService.getAllAwsAccounts().stream().map(AwsAccountResponse::getAccountId).toList();

            logger.debug("Admin/ReadOnly has access to all {} accounts", allAccountIds.size());
            return allAccountIds;
        } else if (UserRole.CUSTOMER.getDisplayName().equalsIgnoreCase(role)) {
            // Customer can only access assigned accounts
            List<String> assignedAccountIds = awsAccountService.getAccountsForUser(userPrincipal.getId()).stream().map(AwsAccount::getAccountId).toList();
            logger.debug("Customer has {} assigned accounts", assignedAccountIds.size());

            if (accountIds != null && !accountIds.isEmpty()) {
                List<String> filteredIds = accountIds.stream().filter(assignedAccountIds::contains).toList();

                logger.debug("Customer requested {} accounts, {} are accessible", accountIds.size(), filteredIds.size());
                return filteredIds;
            }
            return assignedAccountIds;
        }
        logger.warn("Unknown role or no access: {}", role);
        return List.of();
    }

}
