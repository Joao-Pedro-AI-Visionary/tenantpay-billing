package com.tenantpay.billing.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class WebhookIT extends IntegrationTestBase {

    @Test
    void repeatedEventIsAcknowledgedAsDuplicateAndStoredOnce() {
        Account account = signup("Idem Co");
        JsonNode subscription = newSubscription(account);
        String eventId = newEventId();

        Resp first = postWebhook(eventId, "PAYMENT_CONFIRMED", subscription);
        assertThat(first.get("duplicate").asBoolean()).isFalse();
        assertThat(first.text("eventId")).isEqualTo(eventId);
        awaitInboxStatus(eventId, "PROCESSED");

        Resp second = postWebhook(eventId, "PAYMENT_CONFIRMED", subscription);
        assertThat(second.get("received").asBoolean()).isTrue();
        assertThat(second.get("duplicate").asBoolean()).isTrue();
        assertThat(second.text("eventId")).isEqualTo(eventId);

        Integer rows = jdbc.queryForObject("SELECT count(*) FROM webhook_events WHERE event_id = ?", Integer.class,
                eventId);
        assertThat(rows).isEqualTo(1);
        Integer attempts = jdbc.queryForObject("SELECT attempts FROM webhook_events WHERE event_id = ?",
                Integer.class, eventId);
        assertThat(attempts).isEqualTo(1);
        String tenant = jdbc.queryForObject("SELECT tenant_id::text FROM webhook_events WHERE event_id = ?",
                String.class, eventId);
        assertThat(tenant).isEqualTo(account.tenantId());
        assertThat(inboxStatus(eventId)).isEqualTo("PROCESSED");
        assertThat(get("/api/v1/dashboard", account.token()).get("receivedAmountCents").asLong()).isEqualTo(4990);
    }

    @Test
    void missingOrWrongTokenIsRejected() {
        String eventId = newEventId();
        String payload = asaasPayload(eventId, "PAYMENT_CONFIRMED", "pay_fake_x", "ref");

        assertProblem(postWebhook(payload, null), 401);
        assertProblem(postWebhook(payload, "wrong-token"), 401);

        Integer rows = jdbc.queryForObject("SELECT count(*) FROM webhook_events WHERE event_id = ?", Integer.class,
                eventId);
        assertThat(rows).isZero();
    }

    @Test
    void malformedBodiesAreBadRequests() {
        assertProblem(postWebhook("{not json", WEBHOOK_TOKEN), 400);
        assertProblem(postWebhook("{\"event\":\"PAYMENT_CONFIRMED\"}", WEBHOOK_TOKEN), 400);
        assertProblem(postWebhook("", WEBHOOK_TOKEN), 400);
    }

    @Test
    void unknownPaymentIsRetriedWithBackoffAndThenDead() {
        String eventId = newEventId();
        Resp ack = postWebhook(asaasPayload(eventId, "PAYMENT_CONFIRMED", "pay_unknown_" + eventId, "nope"),
                WEBHOOK_TOKEN);
        assertThat(ack.status()).isEqualTo(200);
        assertThat(ack.get("duplicate").asBoolean()).isFalse();

        awaitInboxStatus(eventId, "DEAD");
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT attempts, last_error, next_attempt_at, processed_at, tenant_id FROM webhook_events"
                        + " WHERE event_id = ?", eventId);
        assertThat(row.get("attempts")).isEqualTo(MAX_ATTEMPTS);
        assertThat((String) row.get("last_error")).contains("No charge for gateway payment");
        assertThat(row.get("next_attempt_at")).isNull();
        assertThat(row.get("processed_at")).isNull();
        assertThat(row.get("tenant_id")).isNull();
    }

    @Test
    void overdueThenReceivedMovesSubscriptionAndCharge() {
        Account account = signup("Overdue Co");
        JsonNode subscription = newSubscription(account);
        String id = subscription.get("id").asText();

        postWebhook(newEventId(), "PAYMENT_OVERDUE", subscription);
        JsonNode overdue = awaitSubscriptionStatus(account.token(), id, "OVERDUE");
        assertThat(overdue.get("latestCharge").get("status").asText()).isEqualTo("OVERDUE");
        assertThat(get("/api/v1/dashboard", account.token()).get("receivedAmountCents").asLong()).isZero();

        postWebhook(newEventId(), "PAYMENT_RECEIVED", subscription);
        JsonNode active = awaitSubscriptionStatus(account.token(), id, "ACTIVE");
        assertThat(active.get("latestCharge").get("status").asText()).isEqualTo("RECEIVED");
        assertThat(get("/api/v1/dashboard", account.token()).get("receivedAmountCents").asLong()).isEqualTo(4990);
    }

    @Test
    void receivedActivatesPendingSubscription() {
        Account account = signup("Received Co");
        JsonNode subscription = newSubscription(account);

        postWebhook(newEventId(), "PAYMENT_RECEIVED", subscription);
        JsonNode active = awaitSubscriptionStatus(account.token(), subscription.get("id").asText(), "ACTIVE");
        assertThat(active.get("latestCharge").get("status").asText()).isEqualTo("RECEIVED");
    }

    @Test
    void unknownEventTypeIsIgnored() {
        Account account = signup("Ignore Co");
        JsonNode subscription = newSubscription(account);
        String eventId = newEventId();

        postWebhook(eventId, "PAYMENT_CREATED", subscription);
        awaitInboxStatus(eventId, "IGNORED");
        assertThat(get("/api/v1/subscriptions/" + subscription.get("id").asText(), account.token()).text("status"))
                .isEqualTo("PENDING");
        await().untilAsserted(() -> assertThat(get("/api/v1/admin/webhook-events?status=IGNORED", account.token())
                .body()).anySatisfy(e -> assertThat(e.get("eventId").asText()).isEqualTo(eventId)));
    }
}
