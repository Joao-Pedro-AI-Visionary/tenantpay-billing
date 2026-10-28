package com.tenantpay.billing.web;

import com.tenantpay.billing.application.GetDashboard;
import com.tenantpay.billing.web.dto.BillingDtos.DashboardResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Dashboard")
public class DashboardController {
    private final GetDashboard getDashboard;

    public DashboardController(GetDashboard getDashboard) {
        this.getDashboard = getDashboard;
    }

    @GetMapping("/api/v1/dashboard")
    public DashboardResponse dashboard(@AuthenticationPrincipal Jwt jwt) {
        return DashboardResponse.of(getDashboard.execute(CurrentUser.of(jwt).tenantId()));
    }
}
