package com.tenantpay.billing.web;

import com.tenantpay.billing.application.SimulatePaymentWebhook;
import com.tenantpay.billing.application.exception.NotFoundException;
import com.tenantpay.billing.application.port.PaymentGateway;
import com.tenantpay.billing.web.dto.WebhookDtos.DevConfigResponse;
import com.tenantpay.billing.web.dto.WebhookDtos.SimulateWebhookRequest;
import com.tenantpay.billing.web.dto.WebhookDtos.WebhookAckResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Demo helpers. The simulator answers 404 unless {@code tenantpay.dev-tools.enabled=true}. */
@RestController
@Tag(name = "Dev tools")
public class DevToolsController {
    private final SimulatePaymentWebhook simulate;
    private final PaymentGateway gateway;
    private final boolean enabled;

    public DevToolsController(SimulatePaymentWebhook simulate, PaymentGateway gateway,
                              @Value("${tenantpay.dev-tools.enabled:false}") boolean enabled) {
        this.simulate = simulate;
        this.gateway = gateway;
        this.enabled = enabled;
    }

    @GetMapping("/api/v1/dev/config")
    public DevConfigResponse config() {
        return new DevConfigResponse(enabled, gateway.name());
    }

    @PostMapping("/api/v1/dev/webhooks/simulate")
    public WebhookAckResponse simulate(@AuthenticationPrincipal Jwt jwt,
                                       @Valid @RequestBody SimulateWebhookRequest request) {
        if (!enabled) {
            throw new NotFoundException("Dev tools are disabled");
        }
        return WebhookAckResponse.of(simulate.execute(CurrentUser.of(jwt).tenantId(), request.subscriptionId(),
                request.event()));
    }
}
