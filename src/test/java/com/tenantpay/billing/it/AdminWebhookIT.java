package com.tenantpay.billing.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AdminWebhookIT extends IntegrationTestBase {

    @Test
    void deadEventOfCallerTenantIsReprocessedAndActivatesTheSubscription() {
        Account account = signup("Reprocess Co");
        JsonNode subscription = newSubscription(account);
        String eventId = newEventId();
        UUID id = insertDeadEvent(account.tenantId(), eventId, subscription);

        Resp dead = get("/api/v1/admin/webhook-events?status=DEAD", account.token());
        assertThat(dead.status()).isEqualTo(200);
        assertThat(dead.body()).hasSize(1);
        assertThat(dead.body().get(0).get("id").asText()).isEqualTo(id.toString());
        assertThat(dead.body().get(0).get("attempts").asInt()).isEqualTo(MAX_ATTEMPTS);
        assertThat(dead.body().get(0).get("lastError").asText()).isEqualTo("simulated outage");
        assertThat(get("/api/v1/admin/webhook-events?status=PROCESSED", account.token()).body()).isEmpty();

        Resp reprocess = post("/api/v1/admin/webhook-events/" + id + "/reprocess", account.token(), null);
        assertThat(reprocess.status()).as(reprocess.rawBody()).isEqualTo(202);
        assertThat(reprocess.text("eventId")).isEqualTo(eventId);
        assertThat(reprocess.get("attempts").asInt()).isZero();
        assertThat(reprocess.text("status")).isEqualTo("RECEIVED");

        awaitInboxStatus(eventId, "PROCESSED");
        JsonNode active = awaitSubscriptionStatus(account.token(), subscription.get("id").asText(), "ACTIVE");
        assertThat(active.get("latestCharge").get("status").asText()).isEqualTo("CONFIRMED");

        Resp processed = get("/api/v1/admin/webhook-events?status=PROCESSED", account.token());
        assertThat(processed.body()).hasSize(1);
        assertThat(processed.body().get(0).get("attempts").asInt()).isEqualTo(1);
        assertThat(processed.body().get(0).get("processedAt").isNull()).isFalse();
        assertThat(get("/api/v1/admin/webhook-events", account.token()).body()).hasSize(1);
        assertThat(get("/api/v1/admin/webhook-events?status=DEAD", account.token()).body()).isEmpty();

        assertProblem(post("/api/v1/admin/webhook-events/" + id + "/reprocess", account.token(), null), 409);
        assertProblem(post("/api/v1/admin/webhook-events/" + UUID.randomUUID() + "/reprocess", account.token(),
                null), 404);
    }

    @Test
    void ignoredEventCannotBeReprocessed() {
        Account account = signup("Ignored Reprocess Co");
        JsonNode subscription = newSubscription(account);
        String eventId = newEventId();
        postWebhook(eventId, "PAYMENT_REFUNDED", subscription);
        awaitInboxStatus(eventId, "IGNORED");
        String id = jdbc.queryForObject("SELECT id::text FROM webhook_events WHERE event_id = ?", String.class,
                eventId);

        assertProblem(post("/api/v1/admin/webhook-events/" + id + "/reprocess", account.token(), null), 409);
    }

    @Test
    void adminRoutesNeedAToken() {
        assertProblem(get("/api/v1/admin/webhook-events", null), 401);
    }

    UUID insertDeadEvent(String tenantId, String eventId, JsonNode subscription) {
        JsonNode charge = subscription.get("latestCharge");
        UUID id = UUID.randomUUID();
        jdbc.update("""
                        INSERT INTO webhook_events (id, tenant_id, event_id, event_type, payment_id, payload, status,
                                                    attempts, next_attempt_at, last_error, received_at, processed_at)
                        VALUES (?::uuid, ?::uuid, ?, 'PAYMENT_CONFIRMED', ?, ?::jsonb, 'DEAD', ?, NULL,
                                'simulated outage', now(), NULL)
                        """, id.toString(), tenantId, eventId, charge.get("gatewayPaymentId").asText(),
                asaasPayload(eventId, "PAYMENT_CONFIRMED", charge.get("gatewayPaymentId").asText(),
                        charge.get("id").asText()), MAX_ATTEMPTS);
        return id;
    }
}
