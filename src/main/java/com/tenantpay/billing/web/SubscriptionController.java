package com.tenantpay.billing.web;

import com.tenantpay.billing.application.CancelSubscription;
import com.tenantpay.billing.application.GetSubscription;
import com.tenantpay.billing.application.ListCharges;
import com.tenantpay.billing.application.ListSubscriptions;
import com.tenantpay.billing.application.Subscribe;
import com.tenantpay.billing.domain.SubscriptionStatus;
import com.tenantpay.billing.web.dto.BillingDtos.ChargeResponse;
import com.tenantpay.billing.web.dto.BillingDtos.SubscribeRequest;
import com.tenantpay.billing.web.dto.BillingDtos.SubscriptionResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Subscriptions and charges")
public class SubscriptionController {
    private final Subscribe subscribe;
    private final CancelSubscription cancelSubscription;
    private final ListSubscriptions listSubscriptions;
    private final GetSubscription getSubscription;
    private final ListCharges listCharges;

    public SubscriptionController(Subscribe subscribe, CancelSubscription cancelSubscription,
                                  ListSubscriptions listSubscriptions, GetSubscription getSubscription,
                                  ListCharges listCharges) {
        this.subscribe = subscribe;
        this.cancelSubscription = cancelSubscription;
        this.listSubscriptions = listSubscriptions;
        this.getSubscription = getSubscription;
        this.listCharges = listCharges;
    }

    @PostMapping("/api/v1/subscriptions")
    public ResponseEntity<SubscriptionResponse> subscribe(@AuthenticationPrincipal Jwt jwt,
                                                          @Valid @RequestBody SubscribeRequest request) {
        SubscriptionResponse created = SubscriptionResponse.of(subscribe.execute(new Subscribe.Command(
                CurrentUser.of(jwt).tenantId(), request.customerId(), request.planId())));
        return ResponseEntity.created(URI.create("/api/v1/subscriptions/" + created.id())).body(created);
    }

    @GetMapping("/api/v1/subscriptions")
    public List<SubscriptionResponse> list(@AuthenticationPrincipal Jwt jwt,
                                           @RequestParam(required = false) SubscriptionStatus status) {
        return listSubscriptions.execute(CurrentUser.of(jwt).tenantId(), status).stream()
                .map(SubscriptionResponse::of).toList();
    }

    @GetMapping("/api/v1/subscriptions/{id}")
    public SubscriptionResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return SubscriptionResponse.of(getSubscription.execute(CurrentUser.of(jwt).tenantId(), id));
    }

    @PostMapping("/api/v1/subscriptions/{id}/cancel")
    public SubscriptionResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return SubscriptionResponse.of(cancelSubscription.execute(CurrentUser.of(jwt).tenantId(), id));
    }

    @GetMapping("/api/v1/subscriptions/{id}/charges")
    public List<ChargeResponse> chargesOf(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return listCharges.ofSubscription(CurrentUser.of(jwt).tenantId(), id).stream()
                .map(ChargeResponse::of).toList();
    }

    @GetMapping("/api/v1/charges")
    public List<ChargeResponse> charges(@AuthenticationPrincipal Jwt jwt) {
        return listCharges.all(CurrentUser.of(jwt).tenantId()).stream().map(ChargeResponse::of).toList();
    }
}
