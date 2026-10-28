package com.tenantpay.billing.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.tenantpay.billing.domain.Fixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubscriptionTest {
    private final UUID tenant = UUID.randomUUID();

    @Test
    void startsPendingWithThePlanPrice() {
        Subscription s = Fixtures.subscription(tenant);

        assertThat(s.status()).isEqualTo(SubscriptionStatus.PENDING);
        assertThat(s.amountCents()).isEqualTo(4990);
        assertThat(s.activatedAt()).isNull();
    }

    @Test
    void cannotStartOnInactivePlan() {
        Plan plan = Fixtures.plan(tenant);
        plan.deactivate();

        assertThatThrownBy(() -> Subscription.start(tenant, Fixtures.customer(tenant), plan, NOW))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void cannotMixTenants() {
        assertThatThrownBy(() -> Subscription.start(tenant, Fixtures.customer(UUID.randomUUID()),
                Fixtures.plan(tenant), NOW)).isInstanceOf(DomainValidationException.class);
    }

    @Test
    void activateIsIdempotentAndKeepsFirstActivationTime() {
        Subscription s = Fixtures.subscription(tenant);

        assertThat(s.activate(NOW)).isTrue();
        assertThat(s.activate(NOW.plusSeconds(60))).isFalse();

        assertThat(s.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(s.activatedAt()).isEqualTo(NOW);
    }

    @Test
    void overdueThenPaidReturnsToActive() {
        Subscription s = Fixtures.subscription(tenant);
        s.activate(NOW);

        assertThat(s.markOverdue()).isTrue();
        assertThat(s.markOverdue()).isFalse();
        assertThat(s.status()).isEqualTo(SubscriptionStatus.OVERDUE);

        assertThat(s.activate(NOW.plusSeconds(10))).isTrue();
        assertThat(s.status()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void cancelOnlyOnce() {
        Subscription s = Fixtures.subscription(tenant);
        s.cancel(NOW);

        assertThat(s.status()).isEqualTo(SubscriptionStatus.CANCELED);
        assertThat(s.canceledAt()).isEqualTo(NOW);
        assertThatThrownBy(() -> s.cancel(NOW)).isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void canceledSubscriptionCannotBeActivatedOrMarkedOverdue() {
        Subscription s = Fixtures.subscription(tenant);
        s.cancel(NOW);

        assertThatThrownBy(() -> s.activate(NOW)).isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(s::markOverdue).isInstanceOf(InvalidStateTransitionException.class);
    }
}
