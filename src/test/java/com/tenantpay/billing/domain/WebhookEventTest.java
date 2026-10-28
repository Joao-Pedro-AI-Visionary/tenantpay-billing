package com.tenantpay.billing.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static com.tenantpay.billing.domain.Fixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookEventTest {
    private static final Duration LEASE = Duration.ofMinutes(5);
    private final RetryPolicy policy = new RetryPolicy(3, Duration.ofSeconds(2), Duration.ofMinutes(1));

    private WebhookEvent newEvent() {
        return WebhookEvent.receive("evt_1", "PAYMENT_CONFIRMED", "pay_1", "{\"id\":\"evt_1\"}", null, NOW);
    }

    @Test
    void receivedEventStartsWithoutAttempts() {
        WebhookEvent e = newEvent();

        assertThat(e.status()).isEqualTo(WebhookEventStatus.RECEIVED);
        assertThat(e.attempts()).isZero();
        assertThat(e.nextAttemptAt()).isNull();
    }

    @Test
    void processingCountsTheAttemptAndHoldsALease() {
        WebhookEvent e = newEvent();
        e.startProcessing(NOW, LEASE);

        assertThat(e.status()).isEqualTo(WebhookEventStatus.PROCESSING);
        assertThat(e.attempts()).isEqualTo(1);
        assertThat(e.nextAttemptAt()).isEqualTo(NOW.plus(LEASE));

        e.markProcessed(NOW.plusSeconds(1));
        assertThat(e.status()).isEqualTo(WebhookEventStatus.PROCESSED);
        assertThat(e.processedAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(e.nextAttemptAt()).isNull();
    }

    @Test
    void failuresBackOffExponentiallyThenDie() {
        WebhookEvent e = newEvent();

        e.startProcessing(NOW, LEASE);
        e.markFailed("boom", NOW, policy);
        assertThat(e.status()).isEqualTo(WebhookEventStatus.FAILED);
        assertThat(e.nextAttemptAt()).isEqualTo(NOW.plusSeconds(2));
        assertThat(e.lastError()).isEqualTo("boom");

        e.startProcessing(NOW.plusSeconds(2), LEASE);
        e.markFailed("boom", NOW.plusSeconds(2), policy);
        assertThat(e.nextAttemptAt()).isEqualTo(NOW.plusSeconds(2 + 4));

        e.startProcessing(NOW.plusSeconds(6), LEASE);
        e.markFailed("still broken", NOW.plusSeconds(6), policy);
        assertThat(e.status()).isEqualTo(WebhookEventStatus.DEAD);
        assertThat(e.attempts()).isEqualTo(3);
        assertThat(e.nextAttemptAt()).isNull();
        assertThat(e.lastError()).isEqualTo("still broken");
    }

    @Test
    void longErrorsAreTruncated() {
        WebhookEvent e = newEvent();
        e.startProcessing(NOW, LEASE);
        e.markFailed("x".repeat(5000), NOW, policy);

        assertThat(e.lastError()).hasSize(WebhookEvent.MAX_ERROR_LENGTH);
    }

    @Test
    void reprocessOnlyFromFailedOrDead() {
        WebhookEvent e = newEvent();
        assertThatThrownBy(e::resetForReprocess).isInstanceOf(InvalidStateTransitionException.class);

        e.startProcessing(NOW, LEASE);
        e.markFailed("boom", NOW, policy);
        e.resetForReprocess();

        assertThat(e.status()).isEqualTo(WebhookEventStatus.RECEIVED);
        assertThat(e.attempts()).isZero();
        assertThat(e.nextAttemptAt()).isNull();

        e.startProcessing(NOW, LEASE);
        e.markIgnored(NOW);
        assertThatThrownBy(e::resetForReprocess).isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void finishedOrLeasedEventsCannotBeClaimedAgain() {
        WebhookEvent e = newEvent();
        e.startProcessing(NOW, LEASE);

        assertThatThrownBy(() -> e.startProcessing(NOW.plusSeconds(1), LEASE))
                .isInstanceOf(InvalidStateTransitionException.class);
        // an expired lease (worker crashed) can be taken over
        e.startProcessing(NOW.plus(LEASE), LEASE);
        assertThat(e.attempts()).isEqualTo(2);

        e.markProcessed(NOW);
        assertThatThrownBy(() -> e.startProcessing(NOW, LEASE)).isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(() -> e.markFailed("x", NOW, policy)).isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void workerCrashOnTheLastAttemptIsDetectedAndMovesToDead() {
        WebhookEvent e = newEvent();
        Instant t = NOW;
        for (int attempt = 1; attempt <= policy.maxAttempts(); attempt++) {
            assertThat(e.leaseExpiredOnLastAttempt(t, policy)).isFalse();
            e.startProcessing(t, LEASE);
            t = t.plus(LEASE); // the worker crashed: nobody marked the attempt, the lease simply ran out
        }

        assertThat(e.attempts()).isEqualTo(policy.maxAttempts());
        assertThat(e.leaseExpiredOnLastAttempt(t, policy)).isTrue();
        e.markAbandoned(t);

        assertThat(e.status()).isEqualTo(WebhookEventStatus.DEAD);
        assertThat(e.nextAttemptAt()).isNull();
        assertThat(e.lastError()).contains("lease expired");
    }

    @Test
    void leaseStillRunningIsNotAbandoned() {
        WebhookEvent e = newEvent();
        for (int attempt = 1; attempt <= policy.maxAttempts(); attempt++) {
            e.startProcessing(NOW.plus(LEASE.multipliedBy(attempt - 1)), LEASE);
        }
        Instant beforeLeaseEnds = e.nextAttemptAt().minusSeconds(1);

        assertThat(e.leaseExpiredOnLastAttempt(beforeLeaseEnds, policy)).isFalse();
    }

    @Test
    void tenantCanBeBoundOnceOnly() {
        WebhookEvent e = newEvent();
        UUID owner = UUID.randomUUID();
        e.assignTenant(owner);
        e.assignTenant(owner);

        assertThat(e.tenantId()).isEqualTo(owner);
        assertThatThrownBy(() -> e.assignTenant(UUID.randomUUID())).isInstanceOf(DomainValidationException.class);
    }

    @Test
    void payloadIsRequired() {
        assertThatThrownBy(() -> WebhookEvent.receive("evt", "PAYMENT_CONFIRMED", null, " ", null, NOW))
                .isInstanceOf(DomainValidationException.class);
    }
}
