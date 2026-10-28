package com.tenantpay.billing.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TenantIsolationIT extends IntegrationTestBase {

    @Test
    void tenantCannotSeeOrTouchAnotherTenantsData() {
        Account a = signup("Tenant A");
        Account b = signup("Tenant B");
        JsonNode planA = createPlan(a.token(), "A plan", 4990);
        JsonNode customerA = createCustomer(a.token(), "A customer");
        JsonNode subA = subscribe(a.token(), customerA.get("id").asText(), planA.get("id").asText());
        String planAId = planA.get("id").asText();
        String customerAId = customerA.get("id").asText();
        String subAId = subA.get("id").asText();

        String eventId = newEventId();
        postWebhook(eventId, "PAYMENT_CONFIRMED", subA);
        awaitInboxStatus(eventId, "PROCESSED");
        String eventRowId = jdbc.queryForObject("SELECT id::text FROM webhook_events WHERE event_id = ?",
                String.class, eventId);

        // reads by id
        assertProblem(get("/api/v1/plans/" + planAId, b.token()), 404);
        assertProblem(get("/api/v1/customers/" + customerAId, b.token()), 404);
        assertProblem(get("/api/v1/subscriptions/" + subAId, b.token()), 404);
        assertProblem(get("/api/v1/subscriptions/" + subAId + "/charges", b.token()), 404);

        // lists
        assertThat(get("/api/v1/plans", b.token()).body()).isEmpty();
        assertThat(get("/api/v1/customers", b.token()).body()).isEmpty();
        assertThat(get("/api/v1/subscriptions", b.token()).body()).isEmpty();
        assertThat(get("/api/v1/charges", b.token()).body()).isEmpty();
        assertThat(get("/api/v1/admin/webhook-events", b.token()).body()).isEmpty();
        JsonNode dashboardB = get("/api/v1/dashboard", b.token()).body();
        assertThat(dashboardB.get("plans").asLong()).isZero();
        assertThat(dashboardB.get("subscriptions").asLong()).isZero();
        assertThat(dashboardB.get("receivedAmountCents").asLong()).isZero();

        // writes against A's resources
        assertProblem(put("/api/v1/plans/" + planAId, b.token(),
                Map.of("name", "Hijack", "amountCents", 100, "interval", "MONTHLY")), 404);
        assertProblem(delete("/api/v1/plans/" + planAId, b.token()), 404);
        assertProblem(post("/api/v1/subscriptions/" + subAId + "/cancel", b.token(), null), 404);
        assertProblem(post("/api/v1/admin/webhook-events/" + eventRowId + "/reprocess", b.token(), null), 404);
        assertProblem(post("/api/v1/dev/webhooks/simulate", b.token(),
                Map.of("subscriptionId", subAId, "event", "PAYMENT_CONFIRMED")), 404);

        // subscribing with A's customer or plan
        JsonNode planB = createPlan(b.token(), "B plan", 1990);
        JsonNode customerB = createCustomer(b.token(), "B customer");
        assertProblem(post("/api/v1/subscriptions", b.token(),
                Map.of("customerId", customerAId, "planId", planB.get("id").asText())), 404);
        assertProblem(post("/api/v1/subscriptions", b.token(),
                Map.of("customerId", customerB.get("id").asText(), "planId", planAId)), 404);

        // A is untouched
        JsonNode planAfter = get("/api/v1/plans/" + planAId, a.token()).body();
        assertThat(planAfter.get("name").asText()).isEqualTo("A plan");
        assertThat(planAfter.get("active").asBoolean()).isTrue();
        assertThat(get("/api/v1/subscriptions/" + subAId, a.token()).text("status")).isEqualTo("ACTIVE");
        assertThat(get("/api/v1/admin/webhook-events", a.token()).body()).hasSize(1);
        assertThat(get("/api/v1/subscriptions", b.token()).body()).isEmpty();
    }
}
