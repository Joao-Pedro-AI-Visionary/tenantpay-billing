package com.tenantpay.billing.application;

import com.tenantpay.billing.application.exception.NotFoundException;
import com.tenantpay.billing.domain.Charge;
import com.tenantpay.billing.domain.ChargeStatus;
import com.tenantpay.billing.domain.Fixtures;
import com.tenantpay.billing.domain.InvalidStateTransitionException;
import com.tenantpay.billing.domain.RetryPolicy;
import com.tenantpay.billing.domain.Subscription;
import com.tenantpay.billing.domain.SubscriptionStatus;
import com.tenantpay.billing.domain.WebhookEvent;
import com.tenantpay.billing.domain.WebhookEventStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookUseCasesTest {
    private final UUID tenant = UUID.randomUUID();
    private final InMemoryPorts.Subscriptions subscriptions = new InMemoryPorts.Subscriptions();
    private final InMemoryPorts.Charges charges = new InMemoryPorts.Charges();
    private final InMemoryPorts.Customers customers = new InMemoryPorts.Customers();
    private final InMemoryPorts.WebhookEvents events = new InMemoryPorts.WebhookEvents();
    private final InMemoryPorts.CountingUnitOfWork uow = new InMemoryPorts.CountingUnitOfWork();
    private final MutableClock clock = new MutableClock(Fixtures.NOW);
    private final RetryPolicy policy = new RetryPolicy(3, Duration.ofSeconds(2), Duration.ofMinutes(1));
    private final IngestWebhook ingest = new IngestWebhook(events, charges, uow, clock);
    private final ProcessWebhookEvents process = new ProcessWebhookEvents(events, charges, subscriptions, uow, clock,
            policy, 20, Duration.ofMinutes(5));

    private Subscription subscription;
    private Charge charge;

    @BeforeEach
    void seed() {
        subscription = Fixtures.subscription(tenant);
        charge = Fixtures.charge(subscription, "pay_abc");
        subscriptions.save(subscription);
        charges.save(charge);
    }

    private IngestWebhook.Result send(String eventId, String type, String paymentId) {
        return ingest.execute(new IngestWebhook.Command(eventId, type, paymentId, "{\"id\":\"" + eventId + "\"}"));
    }

    private WebhookEvent only() {
        assertThat(events.rows).hasSize(1);
        return events.rows.values().iterator().next();
    }

    @Test
    void ingestionStoresTheEventWithItsTenantAndDoesNotProcessIt() {
        IngestWebhook.Result result = send("evt_1", "PAYMENT_CONFIRMED", "pay_abc");

        assertThat(result.duplicate()).isFalse();
        WebhookEvent stored = only();
        assertThat(stored.status()).isEqualTo(WebhookEventStatus.RECEIVED);
        assertThat(stored.tenantId()).isEqualTo(tenant);
        assertThat(subscription.status()).isEqualTo(SubscriptionStatus.PENDING);
    }

    @Test
    void duplicateEventIdIsAcknowledgedWithoutAnotherRow() {
        send("evt_1", "PAYMENT_CONFIRMED", "pay_abc");
        IngestWebhook.Result second = send("evt_1", "PAYMENT_CONFIRMED", "pay_abc");

        assertThat(second.duplicate()).isTrue();
        assertThat(second.eventId()).isEqualTo("evt_1");
        assertThat(events.rows).hasSize(1);
    }

    @Test
    void lostInsertRaceIsReportedAsDuplicate() {
        events.loseNextInsertRace = true;

        assertThat(send("evt_race", "PAYMENT_CONFIRMED", "pay_abc").duplicate()).isTrue();
    }

    @Test
    void unknownPaymentIsStoredWithoutTenant() {
        send("evt_x", "PAYMENT_CONFIRMED", "pay_unknown");

        assertThat(only().tenantId()).isNull();
    }

    @Test
    void confirmedPaymentActivatesTheSubscription() {
        send("evt_1", "PAYMENT_CONFIRMED", "pay_abc");

        assertThat(process.processDue()).isEqualTo(1);

        assertThat(only().status()).isEqualTo(WebhookEventStatus.PROCESSED);
        assertThat(only().attempts()).isEqualTo(1);
        assertThat(charge.status()).isEqualTo(ChargeStatus.CONFIRMED);
        assertThat(subscription.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscriptions.locked).as("payment path locks the subscription row").containsExactly(subscription.id());
        assertThat(process.processDue()).isZero();
    }

    @Test
    void eventWhoseLastAttemptCrashedTheWorkerGoesDeadInsteadOfLooping() {
        send("evt_crash", "PAYMENT_CONFIRMED", "pay_abc");
        WebhookEvent event = only();
        for (int attempt = 1; attempt <= policy.maxAttempts(); attempt++) {
            event.startProcessing(clock.instant(), Duration.ofMinutes(5)); // claimed, then the worker died
            clock.advance(Duration.ofMinutes(6));
        }

        assertThat(process.processDue()).as("nothing is handed to a handler").isZero();
        assertThat(only().status()).isEqualTo(WebhookEventStatus.DEAD);
        assertThat(only().attempts()).isEqualTo(policy.maxAttempts());
        assertThat(subscription.status()).isEqualTo(SubscriptionStatus.PENDING);
    }

    @Test
    void replayingAPaymentIsIdempotent() {
        send("evt_1", "PAYMENT_RECEIVED", "pay_abc");
        send("evt_2", "PAYMENT_RECEIVED", "pay_abc");

        assertThat(process.processDue()).isEqualTo(2);

        assertThat(events.rows.values()).allMatch(e -> e.status() == WebhookEventStatus.PROCESSED);
        assertThat(charge.status()).isEqualTo(ChargeStatus.RECEIVED);
        assertThat(subscription.status()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void overdueMarksChargeAndSubscription() {
        send("evt_1", "PAYMENT_OVERDUE", "pay_abc");
        process.processDue();

        assertThat(charge.status()).isEqualTo(ChargeStatus.OVERDUE);
        assertThat(subscription.status()).isEqualTo(SubscriptionStatus.OVERDUE);
    }

    @Test
    void lateOverdueNoticeDoesNotUndoAPayment() {
        charge.confirm(Fixtures.NOW);
        subscription.activate(Fixtures.NOW);
        send("evt_1", "PAYMENT_OVERDUE", "pay_abc");

        process.processDue();

        assertThat(only().status()).isEqualTo(WebhookEventStatus.PROCESSED);
        assertThat(charge.status()).isEqualTo(ChargeStatus.CONFIRMED);
        assertThat(subscription.status()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void otherEventTypesAreIgnored() {
        send("evt_1", "PAYMENT_CREATED", "pay_abc");
        process.processDue();

        assertThat(only().status()).isEqualTo(WebhookEventStatus.IGNORED);
        assertThat(subscription.status()).isEqualTo(SubscriptionStatus.PENDING);
    }

    @Test
    void unknownPaymentFailsWithBackoffAndDiesAfterMaxAttempts() {
        send("evt_1", "PAYMENT_CONFIRMED", "pay_unknown");

        process.processDue();
        WebhookEvent event = only();
        assertThat(event.status()).isEqualTo(WebhookEventStatus.FAILED);
        assertThat(event.attempts()).isEqualTo(1);
        assertThat(event.nextAttemptAt()).isEqualTo(Fixtures.NOW.plusSeconds(2));
        assertThat(event.lastError()).contains("pay_unknown");

        // not due yet: nothing is claimed
        assertThat(process.processDue()).isZero();

        clock.advance(Duration.ofSeconds(2));
        process.processDue();
        assertThat(event.status()).isEqualTo(WebhookEventStatus.FAILED);
        assertThat(event.nextAttemptAt()).isEqualTo(clock.instant().plusSeconds(4));

        clock.advance(Duration.ofSeconds(4));
        process.processDue();
        assertThat(event.status()).isEqualTo(WebhookEventStatus.DEAD);
        assertThat(event.attempts()).isEqualTo(3);

        clock.advance(Duration.ofHours(1));
        assertThat(process.processDue()).isZero();
    }

    @Test
    void reprocessRevivesADeadEventOfTheSameTenantOnly() {
        send("evt_1", "PAYMENT_CONFIRMED", "pay_abc");
        WebhookEvent event = only();
        event.startProcessing(Fixtures.NOW, Duration.ofMinutes(5));
        for (int i = 0; i < 3; i++) {
            event.markFailed("boom", Fixtures.NOW, policy);
            if (event.status() == WebhookEventStatus.FAILED) {
                event.startProcessing(event.nextAttemptAt(), Duration.ofMinutes(5));
            }
        }
        assertThat(event.status()).isEqualTo(WebhookEventStatus.DEAD);

        ReprocessWebhookEvent reprocess = new ReprocessWebhookEvent(events, uow);
        assertThatThrownBy(() -> reprocess.execute(UUID.randomUUID(), event.id()))
                .isInstanceOf(NotFoundException.class);

        WebhookEvent revived = reprocess.execute(tenant, event.id());
        assertThat(revived.status()).isEqualTo(WebhookEventStatus.RECEIVED);
        assertThat(revived.attempts()).isZero();

        process.processDue();
        assertThat(event.status()).isEqualTo(WebhookEventStatus.PROCESSED);
        assertThat(subscription.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThatThrownBy(() -> reprocess.execute(tenant, event.id()))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void simulatorBuildsAnAsaasPayloadForTheLatestCharge() {
        customers.save(com.tenantpay.billing.domain.Customer.restore(subscription.customerId(), tenant, "Jane",
                "jane@example.test", "24971563792", "cus_test", Fixtures.NOW));
        SimulatePaymentWebhook simulate = new SimulatePaymentWebhook(subscriptions, charges, customers, ingest, uow,
                clock);

        IngestWebhook.Result result = simulate.execute(tenant, subscription.id(), "PAYMENT_CONFIRMED");

        assertThat(result.duplicate()).isFalse();
        assertThat(result.eventId()).startsWith("evt_sim_");
        WebhookEvent stored = only();
        assertThat(stored.paymentId()).isEqualTo("pay_abc");
        assertThat(stored.payload()).contains("\"event\":\"PAYMENT_CONFIRMED\"", "\"value\":49.90",
                "\"externalReference\":\"" + charge.id() + "\"");
        assertThatThrownBy(() -> simulate.execute(UUID.randomUUID(), subscription.id(), "PAYMENT_CONFIRMED"))
                .isInstanceOf(NotFoundException.class);
    }
}
