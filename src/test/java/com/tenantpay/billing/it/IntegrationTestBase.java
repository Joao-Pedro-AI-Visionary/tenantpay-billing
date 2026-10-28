package com.tenantpay.billing.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Shared harness for the HTTP integration tests. A single PostgreSQL container is started once per JVM and reused by
 * every IT class; since all subclasses share the same configuration, Spring also reuses one application context.
 * Requests go through a real HTTP client against the random port, so the full security chain and JSON mapping run.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "tenantpay.asaas.api-key=",
        "tenantpay.asaas.webhook-token=" + IntegrationTestBase.WEBHOOK_TOKEN,
        "tenantpay.dev-tools.enabled=true",
        "tenantpay.webhooks.poll-interval=PT0.05S",
        "tenantpay.webhooks.base-backoff=PT0.05S",
        "tenantpay.webhooks.max-backoff=PT0.2S",
        "tenantpay.webhooks.max-attempts=" + IntegrationTestBase.MAX_ATTEMPTS
})
public abstract class IntegrationTestBase {
    static final String WEBHOOK_TOKEN = "it-webhook-token";
    static final int MAX_ATTEMPTS = 3;
    static final String DOCUMENT = "24971563792";

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void awaitDefaults() {
        org.awaitility.Awaitility.setDefaultTimeout(Duration.ofSeconds(15));
        org.awaitility.Awaitility.setDefaultPollInterval(Duration.ofMillis(50));
    }

    // ---------------------------------------------------------------- HTTP

    record Resp(int status, String contentType, String rawBody, JsonNode body) {
        JsonNode get(String field) {
            return body.get(field);
        }

        String text(String field) {
            JsonNode node = body.get(field);
            return node == null || node.isNull() ? null : node.asText();
        }
    }

    Resp call(String method, String path, String token, Object body, Map<String, String> headers) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json, application/problem+json");
            if (token != null) {
                builder.header("Authorization", "Bearer " + token);
            }
            headers.forEach(builder::header);
            if (body == null) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                String json = body instanceof String s ? s : objectMapper.writeValueAsString(body);
                builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(json));
            }
            HttpResponse<String> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            String raw = response.body();
            JsonNode node = raw == null || raw.isBlank() ? objectMapper.nullNode() : objectMapper.readTree(raw);
            return new Resp(response.statusCode(), response.headers().firstValue("Content-Type").orElse(""), raw,
                    node);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    Resp get(String path, String token) {
        return call("GET", path, token, null, Map.of());
    }

    Resp post(String path, String token, Object body) {
        return call("POST", path, token, body, Map.of());
    }

    Resp put(String path, String token, Object body) {
        return call("PUT", path, token, body, Map.of());
    }

    Resp delete(String path, String token) {
        return call("DELETE", path, token, null, Map.of());
    }

    static void assertProblem(Resp resp, int status) {
        assertThat(resp.status()).as(resp.rawBody()).isEqualTo(status);
        assertThat(resp.contentType()).startsWith("application/problem+json");
        assertThat(resp.body().get("status").asInt()).isEqualTo(status);
        assertThat(resp.text("title")).isNotBlank();
    }

    // ---------------------------------------------------------------- fixtures

    record Account(String email, String password, String token, String tenantId, String userId) {
    }

    static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@it.test";
    }

    Account signup(String tenantName) {
        String email = uniqueEmail("owner");
        String password = UUID.randomUUID().toString();
        Resp resp = post("/api/v1/auth/signup", null,
                Map.of("tenantName", tenantName, "email", email, "password", password));
        assertThat(resp.status()).as(resp.rawBody()).isEqualTo(201);
        return new Account(email, password, resp.text("accessToken"), resp.text("tenantId"), resp.text("userId"));
    }

    JsonNode createPlan(String token, String name, long amountCents) {
        Resp resp = post("/api/v1/plans", token,
                Map.of("name", name, "description", name + " plan", "amountCents", amountCents,
                        "interval", "MONTHLY"));
        assertThat(resp.status()).as(resp.rawBody()).isEqualTo(201);
        return resp.body();
    }

    JsonNode createCustomer(String token, String name) {
        Resp resp = post("/api/v1/customers", token,
                Map.of("name", name, "email", uniqueEmail("customer"), "document", DOCUMENT));
        assertThat(resp.status()).as(resp.rawBody()).isEqualTo(201);
        return resp.body();
    }

    JsonNode subscribe(String token, String customerId, String planId) {
        Resp resp = post("/api/v1/subscriptions", token, Map.of("customerId", customerId, "planId", planId));
        assertThat(resp.status()).as(resp.rawBody()).isEqualTo(201);
        return resp.body();
    }

    /** Creates plan, customer and a PENDING subscription for the account. */
    JsonNode newSubscription(Account account) {
        JsonNode plan = createPlan(account.token(), "Pro", 4990);
        JsonNode customer = createCustomer(account.token(), "Jane Doe");
        return subscribe(account.token(), customer.get("id").asText(), plan.get("id").asText());
    }

    static String newEventId() {
        return "evt_it_" + UUID.randomUUID().toString().replace("-", "");
    }

    static String asaasPayload(String eventId, String eventType, String paymentId, String chargeId) {
        return """
                {
                  "id": "%s",
                  "event": "%s",
                  "dateCreated": "2026-10-28 12:00:00",
                  "payment": {
                    "object": "payment", "id": "%s", "customer": "cus_fake_it",
                    "value": 49.90, "netValue": 48.90, "billingType": "PIX", "status": "CONFIRMED",
                    "dueDate": "2026-10-31", "paymentDate": "2026-10-28", "externalReference": "%s",
                    "someUnknownField": {"nested": true}
                  }
                }
                """.formatted(eventId, eventType, paymentId, chargeId);
    }

    Resp postWebhook(String payload, String token) {
        return call("POST", "/webhooks/asaas", null, payload,
                token == null ? Map.of() : Map.of("asaas-access-token", token));
    }

    Resp postWebhook(String eventId, String eventType, JsonNode subscription) {
        JsonNode charge = subscription.get("latestCharge");
        Resp resp = postWebhook(asaasPayload(eventId, eventType, charge.get("gatewayPaymentId").asText(),
                charge.get("id").asText()), WEBHOOK_TOKEN);
        assertThat(resp.status()).as(resp.rawBody()).isEqualTo(200);
        return resp;
    }

    String inboxStatus(String eventId) {
        return jdbc.queryForObject("SELECT status FROM webhook_events WHERE event_id = ?", String.class, eventId);
    }

    void awaitInboxStatus(String eventId, String status) {
        await().untilAsserted(() -> assertThat(inboxStatus(eventId)).isEqualTo(status));
    }

    JsonNode awaitSubscriptionStatus(String token, String subscriptionId, String status) {
        await().untilAsserted(() -> assertThat(
                get("/api/v1/subscriptions/" + subscriptionId, token).text("status")).isEqualTo(status));
        return get("/api/v1/subscriptions/" + subscriptionId, token).body();
    }
}
