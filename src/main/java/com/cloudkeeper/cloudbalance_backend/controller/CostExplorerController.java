package com.cloudkeeper.cloudbalance_backend.controller;

import com.cloudkeeper.cloudbalance_backend.config.UserPrincipal;
import com.cloudkeeper.cloudbalance_backend.dto.request.CostReportRequestDTO;
import com.cloudkeeper.cloudbalance_backend.dto.response.AwsAccountResponse;
import com.cloudkeeper.cloudbalance_backend.dto.response.CostReportResponseDTO;
import com.cloudkeeper.cloudbalance_backend.entity.AwsAccount;
import com.cloudkeeper.cloudbalance_backend.entity.UserRole;
import com.cloudkeeper.cloudbalance_backend.helper.roleAnnotations.AnyAuthenticatedUser;
import com.cloudkeeper.cloudbalance_backend.logging.Logger;
import com.cloudkeeper.cloudbalance_backend.logging.LoggerFactory;
import com.cloudkeeper.cloudbalance_backend.service.AwsAccountService;
import com.cloudkeeper.cloudbalance_backend.service.CostExplorerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/cost-explorer")
@RequiredArgsConstructor
public class CostExplorerController {
    private final CostExplorerService costExplorerService;
    private final AwsAccountService awsAccountService;
    private final Logger logger = LoggerFactory.getLogger(CostExplorerController.class);

    @GetMapping
    @AnyAuthenticatedUser
    public ResponseEntity<CostReportResponseDTO> getCostReport(@AuthenticationPrincipal UserPrincipal userPrincipal, @Valid CostReportRequestDTO request) {
        List<String> accounts = getAccessibleAccountIds(userPrincipal);

        if (accounts.isEmpty()) {
            logger.warn("User {} has no accessible AWS accounts", userPrincipal.getId());
            return ResponseEntity.ok(new CostReportResponseDTO());
        }

        CostReportResponseDTO response = costExplorerService.getCostReport(request, accounts);

        return ResponseEntity.ok(response);
    }


    private List<String> getAccessibleAccountIds(UserPrincipal userPrincipal) {

        if (hasRole(userPrincipal, UserRole.ADMIN) || hasRole(userPrincipal, UserRole.READ_ONLY)) {

            List<String> allAccountIds = awsAccountService.getAllAwsAccounts().stream().map(AwsAccountResponse::getAccountId).toList();

            logger.debug("Admin/ReadOnly has access to {} accounts", allAccountIds.size());
            return allAccountIds;
        }

        if (hasRole(userPrincipal, UserRole.CUSTOMER)) {
            List<String> assignedAccountIds = awsAccountService.getAccountsForUser(userPrincipal.getId()).stream().map(AwsAccount::getAccountId).toList();

            logger.debug("Customer has {} assigned accounts", assignedAccountIds.size());
            return assignedAccountIds;
        }

        logger.warn("Unknown role or no access for user {}", userPrincipal.getUsername());
        return List.of();
    }

    private boolean hasRole(UserPrincipal user, UserRole role) {
        return user.getAuthorities().stream().anyMatch(a -> Objects.requireNonNull(a.getAuthority()).equalsIgnoreCase("ROLE_" + role.name()));
    }

}
