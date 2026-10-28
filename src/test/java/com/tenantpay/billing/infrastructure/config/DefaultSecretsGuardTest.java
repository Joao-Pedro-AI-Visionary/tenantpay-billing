package com.tenantpay.billing.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultSecretsGuardTest {
    private static final String REAL_SECRET = "a-real-secret-that-nobody-published-0123456789";

    private static TenantpayProperties props(String jwtSecret, String webhookToken, boolean devTools) {
        return new TenantpayProperties(
                new TenantpayProperties.Jwt(jwtSecret, Duration.ofHours(1)),
                new TenantpayProperties.Asaas("https://api-sandbox.asaas.com/v3", "", webhookToken),
                new TenantpayProperties.Webhooks(Duration.ofMillis(500), 5, Duration.ofSeconds(2),
                        Duration.ofMinutes(5), 20, Duration.ofMinutes(5)),
                new TenantpayProperties.DevTools(devTools),
                new TenantpayProperties.Cors(List.of("http://localhost:5173")),
                new TenantpayProperties.Scheduling(true));
    }

    @Test
    void publicDefaultsAreRejectedOutsideDevelopment() {
        assertThatThrownBy(() -> DefaultSecretsGuard.verify(
                props("dev-only-secret-change-me-0123456789abcdef", "real-token", false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TENANTPAY_JWT_SECRET")
                .hasMessageNotContaining("TENANTPAY_ASAAS_WEBHOOK_TOKEN");
        assertThatThrownBy(() -> DefaultSecretsGuard.verify(
                props("local-compose-secret-change-me-0123456789abcdef", "dev-webhook-token", false)))
                .hasMessageContaining("TENANTPAY_JWT_SECRET")
                .hasMessageContaining("TENANTPAY_ASAAS_WEBHOOK_TOKEN");
        assertThatThrownBy(() -> DefaultSecretsGuard.verify(props(REAL_SECRET, "dev-webhook-token", false)))
                .hasMessageContaining("TENANTPAY_ASAAS_WEBHOOK_TOKEN");
    }

    @Test
    void publicDefaultsAreAllowedWithDevToolsEnabled() {
        assertThatCode(() -> DefaultSecretsGuard.verify(
                props("dev-only-secret-change-me-0123456789abcdef", "dev-webhook-token", true)))
                .doesNotThrowAnyException();
    }

    @Test
    void realSecretsAlwaysPass() {
        assertThatCode(() -> DefaultSecretsGuard.verify(props(REAL_SECRET, "real-token", false)))
                .doesNotThrowAnyException();
    }
}
