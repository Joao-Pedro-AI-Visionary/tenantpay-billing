package com.tenantpay.billing.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BillingFlowIT extends IntegrationTestBase {

    @Test
    void signupLoginSubscribeAndConfirmPaymentThroughTheWebhook() {
        Account account = signup("Acme Gym");
        assertThat(account.token()).isNotBlank();

        Resp login = post("/api/v1/auth/login", null,
                Map.of("email", account.email(), "password", account.password()));
        assertThat(login.status()).isEqualTo(200);
        assertThat(login.text("tokenType")).isEqualTo("Bearer");
        assertThat(login.get("expiresIn").asLong()).isEqualTo(3600);
        assertThat(login.text("tenantId")).isEqualTo(account.tenantId());
        String token = login.text("accessToken");

        Resp me = get("/api/v1/me", token);
        assertThat(me.status()).isEqualTo(200);
        assertThat(me.text("tenantId")).isEqualTo(account.tenantId());
        assertThat(me.text("userId")).isEqualTo(account.userId());
        assertThat(me.text("tenantName")).isEqualTo("Acme Gym");
        assertThat(me.text("email")).isEqualTo(account.email());
        assertThat(me.text("role")).isEqualTo("ADMIN");

        JsonNode plan = createPlan(token, "Pro", 4990);
        assertThat(plan.get("currency").asText()).isEqualTo("BRL");
        assertThat(plan.get("interval").asText()).isEqualTo("MONTHLY");
        assertThat(plan.get("active").asBoolean()).isTrue();

        JsonNode customer = createCustomer(token, "Jane Doe");
        assertThat(customer.get("gatewayCustomerId").asText()).startsWith("cus_fake_");
        assertThat(customer.get("document").asText()).isEqualTo(DOCUMENT);

        JsonNode subscription = subscribe(token, customer.get("id").asText(), plan.get("id").asText());
        String subscriptionId = subscription.get("id").asText();
        assertThat(subscription.get("status").asText()).isEqualTo("PENDING");
        assertThat(subscription.get("customerName").asText()).isEqualTo("Jane Doe");
        assertThat(subscription.get("planName").asText()).isEqualTo("Pro");
        assertThat(subscription.get("amountCents").asLong()).isEqualTo(4990);
        assertThat(subscription.get("activatedAt").isNull()).isTrue();
        JsonNode latestCharge = subscription.get("latestCharge");
        assertThat(latestCharge.get("status").asText()).isEqualTo("PENDING");
        assertThat(latestCharge.get("gatewayPaymentId").asText()).startsWith("pay_fake_");
        assertThat(latestCharge.get("amountCents").asLong()).isEqualTo(4990);
        assertThat(LocalDate.parse(latestCharge.get("dueDate").asText()))
                .isEqualTo(LocalDate.now(ZoneOffset.UTC).plusDays(3));

        Resp ack = postWebhook(newEventId(), "PAYMENT_CONFIRMED", subscription);
        assertThat(ack.get("received").asBoolean()).isTrue();
        assertThat(ack.get("duplicate").asBoolean()).isFalse();

        JsonNode active = awaitSubscriptionStatus(token, subscriptionId, "ACTIVE");
        assertThat(active.get("activatedAt").isNull()).isFalse();
        assertThat(active.get("latestCharge").get("status").asText()).isEqualTo("CONFIRMED");

        Resp charges = get("/api/v1/subscriptions/" + subscriptionId + "/charges", token);
        assertThat(charges.status()).isEqualTo(200);
        assertThat(charges.body()).hasSize(1);
        assertThat(charges.body().get(0).get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(charges.body().get(0).get("paidAt").isNull()).isFalse();
        assertThat(charges.body().get(0).get("subscriptionId").asText()).isEqualTo(subscriptionId);

        Resp allCharges = get("/api/v1/charges", token);
        assertThat(allCharges.status()).isEqualTo(200);
        assertThat(allCharges.body()).hasSize(1);

        Resp activeList = get("/api/v1/subscriptions?status=ACTIVE", token);
        assertThat(activeList.body()).hasSize(1);
        assertThat(get("/api/v1/subscriptions?status=PENDING", token).body()).isEmpty();

        Resp dashboard = get("/api/v1/dashboard", token);
        assertThat(dashboard.status()).isEqualTo(200);
        assertThat(dashboard.get("plans").asLong()).isEqualTo(1);
        assertThat(dashboard.get("customers").asLong()).isEqualTo(1);
        assertThat(dashboard.get("subscriptions").asLong()).isEqualTo(1);
        JsonNode byStatus = dashboard.get("subscriptionsByStatus");
        assertThat(byStatus.get("PENDING").asLong()).isZero();
        assertThat(byStatus.get("ACTIVE").asLong()).isEqualTo(1);
        assertThat(byStatus.get("OVERDUE").asLong()).isZero();
        assertThat(byStatus.get("CANCELED").asLong()).isZero();
        assertThat(dashboard.get("receivedAmountCents").asLong()).isEqualTo(4990);
    }

    @Test
    void cancelingTwiceConflicts() {
        Account account = signup("Cancel Co");
        JsonNode subscription = newSubscription(account);
        String id = subscription.get("id").asText();

        Resp canceled = post("/api/v1/subscriptions/" + id + "/cancel", account.token(), null);
        assertThat(canceled.status()).isEqualTo(200);
        assertThat(canceled.text("status")).isEqualTo("CANCELED");
        assertThat(canceled.get("canceledAt").isNull()).isFalse();

        assertProblem(post("/api/v1/subscriptions/" + id + "/cancel", account.token(), null), 409);
        assertThat(get("/api/v1/dashboard", account.token()).get("subscriptionsByStatus").get("CANCELED").asLong())
                .isEqualTo(1);
    }

    @Test
    void planCrudAndInactivePlanCannotBeSubscribed() {
        Account account = signup("Plans Co");
        String token = account.token();
        JsonNode plan = createPlan(token, "Basic", 1990);
        String planId = plan.get("id").asText();

        Resp updated = put("/api/v1/plans/" + planId, token,
                Map.of("name", "Basic Plus", "description", "more", "amountCents", 2990, "interval", "YEARLY"));
        assertThat(updated.status()).as(updated.rawBody()).isEqualTo(200);
        assertThat(updated.text("name")).isEqualTo("Basic Plus");
        assertThat(updated.get("amountCents").asLong()).isEqualTo(2990);
        assertThat(updated.text("interval")).isEqualTo("YEARLY");

        Resp fetched = get("/api/v1/plans/" + planId, token);
        assertThat(fetched.status()).isEqualTo(200);
        assertThat(fetched.text("name")).isEqualTo("Basic Plus");
        assertThat(fetched.text("description")).isEqualTo("more");

        JsonNode newer = createPlan(token, "Newer", 5000);
        Resp list = get("/api/v1/plans", token);
        assertThat(list.body()).hasSize(2);
        assertThat(list.body().get(0).get("id").asText()).isEqualTo(newer.get("id").asText());

        assertThat(delete("/api/v1/plans/" + planId, token).status()).isEqualTo(204);
        Resp inactive = get("/api/v1/plans/" + planId, token);
        assertThat(inactive.get("active").asBoolean()).isFalse();

        JsonNode customer = createCustomer(token, "John Roe");
        assertProblem(post("/api/v1/subscriptions", token,
                Map.of("customerId", customer.get("id").asText(), "planId", planId)), 409);

        assertThat(get("/api/v1/customers/" + customer.get("id").asText(), token).status()).isEqualTo(200);
        assertThat(get("/api/v1/customers", token).body()).hasSize(1);
        assertProblem(get("/api/v1/plans/" + java.util.UUID.randomUUID(), token), 404);
        assertProblem(put("/api/v1/plans/" + java.util.UUID.randomUUID(), token,
                Map.of("name", "X", "amountCents", 2990, "interval", "YEARLY")), 404);
    }
}
