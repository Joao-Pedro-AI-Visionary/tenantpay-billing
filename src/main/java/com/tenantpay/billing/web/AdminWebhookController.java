package com.tenantpay.billing.web;

import com.tenantpay.billing.application.ListWebhookEvents;
import com.tenantpay.billing.application.ReprocessWebhookEvent;
import com.tenantpay.billing.domain.WebhookEventStatus;
import com.tenantpay.billing.web.dto.WebhookDtos.WebhookEventResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Webhook inbox operations. Role ADMIN is enforced by the security chain; data is scoped to the caller tenant. */
@RestController
@RequestMapping("/api/v1/admin/webhook-events")
@Tag(name = "Admin")
public class AdminWebhookController {
    private final ListWebhookEvents listEvents;
    private final ReprocessWebhookEvent reprocess;

    public AdminWebhookController(ListWebhookEvents listEvents, ReprocessWebhookEvent reprocess) {
        this.listEvents = listEvents;
        this.reprocess = reprocess;
    }

    @GetMapping
    public List<WebhookEventResponse> list(@AuthenticationPrincipal Jwt jwt,
                                           @RequestParam(required = false) WebhookEventStatus status) {
        return listEvents.execute(CurrentUser.of(jwt).tenantId(), status).stream()
                .map(WebhookEventResponse::of).toList();
    }

    @PostMapping("/{id}/reprocess")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public WebhookEventResponse reprocess(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return WebhookEventResponse.of(reprocess.execute(CurrentUser.of(jwt).tenantId(), id));
    }
}
