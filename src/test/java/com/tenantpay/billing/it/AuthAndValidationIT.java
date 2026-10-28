package com.tenantpay.billing.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthAndValidationIT extends IntegrationTestBase {

    @Test
    void invalidSignupReturnsFieldErrors() {
        Resp resp = post("/api/v1/auth/signup", null,
                Map.of("tenantName", "A", "email", "not-an-email", "password", "short"));
        assertProblem(resp, 400);
        assertThat(resp.text("instance")).isEqualTo("/api/v1/auth/signup");
        assertThat(fields(resp)).contains("email", "password", "tenantName");
        resp.get("errors").forEach(error -> assertThat(error.get("message").asText()).isNotBlank());
    }

    @Test
    void invalidPlanCustomerAndSubscriptionBodiesAreRejected() {
        String token = signup("Validation Co").token();

        Resp plan = post("/api/v1/plans", token, Map.of("name", "", "amountCents", 50));
        assertProblem(plan, 400);
        assertThat(fields(plan)).contains("name", "amountCents", "interval");

        Resp customer = post("/api/v1/customers", token,
                Map.of("name", "Jane", "email", "jane@example.test", "document", "123"));
        assertProblem(customer, 400);
        assertThat(fields(customer)).containsExactly("document");

        Resp subscription = post("/api/v1/subscriptions", token, Map.of());
        assertProblem(subscription, 400);
        assertThat(fields(subscription)).contains("customerId", "planId");

        assertProblem(post("/api/v1/plans", token, "{broken"), 400);
        assertProblem(get("/api/v1/plans/not-a-uuid", token), 400);
    }

    @Test
    void protectedRoutesNeedAValidToken() {
        Resp missing = get("/api/v1/me", null);
        assertProblem(missing, 401);
        assertThat(missing.text("instance")).isEqualTo("/api/v1/me");

        assertProblem(get("/api/v1/plans", "not.a.jwt"), 401);
        assertProblem(get("/api/v1/dashboard", null), 401);
    }

    @Test
    void duplicateEmailConflictsAndBadLoginIsUnauthorized() {
        Account account = signup("Dup Co");

        Resp dup = post("/api/v1/auth/signup", null,
                Map.of("tenantName", "Other Co", "email", account.email(), "password", "another-pass"));
        assertProblem(dup, 409);

        assertProblem(post("/api/v1/auth/login", null,
                Map.of("email", account.email(), "password", "wrong-password")), 401);
        assertProblem(post("/api/v1/auth/login", null,
                Map.of("email", uniqueEmail("nobody"), "password", "whatever-pass")), 401);
        assertProblem(post("/api/v1/auth/login", null, Map.of("email", "bad")), 400);
    }

    private static List<String> fields(Resp resp) {
        JsonNode errors = resp.get("errors");
        assertThat(errors).as(resp.rawBody()).isNotNull();
        assertThat(errors.isArray()).isTrue();
        List<String> fields = new ArrayList<>();
        errors.forEach(error -> fields.add(error.get("field").asText()));
        return fields;
    }
}
