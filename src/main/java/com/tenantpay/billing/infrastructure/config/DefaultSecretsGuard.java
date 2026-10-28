package com.tenantpay.billing.infrastructure.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Refuses to start with the development secrets that are published in this repository (application.yml and
 * docker-compose.yml) unless dev tools are explicitly enabled. Anyone who read the public repository could otherwise
 * forge a JWT for any tenant, or a payment webhook, against a deployment that forgot to set its secrets.
 */
public final class DefaultSecretsGuard {
    static final Set<String> PUBLIC_JWT_SECRETS = Set.of(
            "dev-only-secret-change-me-0123456789abcdef",
            "local-compose-secret-change-me-0123456789abcdef");
    static final Set<String> PUBLIC_WEBHOOK_TOKENS = Set.of("dev-webhook-token");

    private DefaultSecretsGuard() {
    }

    public static void verify(TenantpayProperties properties) {
        if (properties.devTools().enabled()) {
            return;
        }
        List<String> problems = new ArrayList<>();
        if (PUBLIC_JWT_SECRETS.contains(properties.jwt().secret())) {
            problems.add("TENANTPAY_JWT_SECRET");
        }
        if (PUBLIC_WEBHOOK_TOKENS.contains(properties.asaas().webhookToken())) {
            problems.add("TENANTPAY_ASAAS_WEBHOOK_TOKEN");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Refusing to start: " + String.join(", ", problems)
                    + " still use the public development default. Set real secrets, or enable dev tools"
                    + " (TENANTPAY_DEV_TOOLS_ENABLED=true) for local development only.");
        }
    }
}
