package com.tenantpay.billing.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Builders shared by the domain and application unit tests. */
public final class Fixtures {
    public static final Instant NOW = Instant.parse("2026-10-28T12:00:00Z");

    private Fixtures() {
    }

    public static Plan plan(UUID tenantId) {
        return Plan.create(tenantId, "Pro", "Pro plan", 4990, PlanInterval.MONTHLY, NOW);
    }

    public static Customer customer(UUID tenantId) {
        return Customer.create(UUID.randomUUID(), tenantId, "Jane Doe", "jane@example.test", "24971563792",
                "cus_test", NOW);
    }

    public static Subscription subscription(UUID tenantId) {
        return Subscription.start(tenantId, customer(tenantId), plan(tenantId), NOW);
    }

    public static Charge charge(Subscription subscription, String paymentId) {
        return Charge.create(UUID.randomUUID(), subscription, paymentId, LocalDate.parse("2026-10-31"), NOW);
    }
}
