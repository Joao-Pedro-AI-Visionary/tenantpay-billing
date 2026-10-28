package com.tenantpay.billing.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/** Typed view of the {@code tenantpay.*} properties. Defaults live in application.yml and here. */
@ConfigurationProperties(prefix = "tenantpay")
public record TenantpayProperties(
        @DefaultValue Jwt jwt,
        @DefaultValue Asaas asaas,
        @DefaultValue Webhooks webhooks,
        @DefaultValue DevTools devTools,
        @DefaultValue Cors cors,
        @DefaultValue Scheduling scheduling) {

    public record Jwt(String secret, @DefaultValue("PT1H") Duration ttl) {
    }

    public record Asaas(@DefaultValue("https://api-sandbox.asaas.com/v3") String baseUrl,
                        @DefaultValue("") String apiKey,
                        @DefaultValue("dev-webhook-token") String webhookToken) {
    }

    public record Webhooks(@DefaultValue("PT0.5S") Duration pollInterval,
                           @DefaultValue("5") int maxAttempts,
                           @DefaultValue("PT2S") Duration baseBackoff,
                           @DefaultValue("PT5M") Duration maxBackoff,
                           @DefaultValue("20") int batchSize,
                           @DefaultValue("PT5M") Duration processingLease) {
    }

    public record DevTools(@DefaultValue("false") boolean enabled) {
    }

    public record Cors(@DefaultValue({"http://localhost:5173", "http://localhost:15173"}) List<String> allowedOrigins) {
    }

    public record Scheduling(@DefaultValue("true") boolean enabled) {
    }
}
