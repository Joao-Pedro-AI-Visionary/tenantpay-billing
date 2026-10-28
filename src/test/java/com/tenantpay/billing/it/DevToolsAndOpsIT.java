package com.tenantpay.billing.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DevToolsAndOpsIT extends IntegrationTestBase {

    @Test
    void simulateConfirmedActivatesTheSubscription() {
        Account account = signup("Sim Co");
        JsonNode subscription = newSubscription(account);
        String id = subscription.get("id").asText();

        Resp ack = post("/api/v1/dev/webhooks/simulate", account.token(),
                Map.of("subscriptionId", id, "event", "PAYMENT_CONFIRMED"));
        assertThat(ack.status()).as(ack.rawBody()).isEqualTo(200);
        assertThat(ack.get("received").asBoolean()).isTrue();
        assertThat(ack.get("duplicate").asBoolean()).isFalse();
        assertThat(ack.text("eventId")).startsWith("evt_sim_");

        JsonNode active = awaitSubscriptionStatus(account.token(), id, "ACTIVE");
        assertThat(active.get("latestCharge").get("status").asText()).isEqualTo("CONFIRMED");
        String payload = jdbc.queryForObject("SELECT payload::text FROM webhook_events WHERE event_id = ?",
                String.class, ack.text("eventId"));
        assertThat(payload).contains(subscription.get("latestCharge").get("gatewayPaymentId").asText());
    }

    @Test
    void simulateValidatesItsInput() {
        Account account = signup("Sim Validation Co");
        assertProblem(post("/api/v1/dev/webhooks/simulate", account.token(),
                Map.of("subscriptionId", java.util.UUID.randomUUID().toString(), "event", "PAYMENT_CONFIRMED")),
                404);
        assertProblem(post("/api/v1/dev/webhooks/simulate", account.token(), Map.of("event", "lower")), 400);
        assertProblem(post("/api/v1/dev/webhooks/simulate", null,
                Map.of("subscriptionId", java.util.UUID.randomUUID().toString(), "event", "PAYMENT_CONFIRMED")),
                401);
    }

    @Test
    void devConfigIsPublic() {
        Resp config = get("/api/v1/dev/config", null);
        assertThat(config.status()).isEqualTo(200);
        assertThat(config.get("devTools").asBoolean()).isTrue();
        assertThat(config.text("gateway")).isEqualTo("fake");
    }

    @Test
    void openApiAndHealthArePublic() {
        Resp docs = get("/v3/api-docs", null);
        assertThat(docs.status()).isEqualTo(200);
        assertThat(docs.get("paths").has("/api/v1/plans")).isTrue();
        assertThat(docs.get("paths").has("/webhooks/asaas")).isTrue();

        Resp health = get("/actuator/health", null);
        assertThat(health.status()).isEqualTo(200);
        assertThat(health.text("status")).isEqualTo("UP");
    }
}
