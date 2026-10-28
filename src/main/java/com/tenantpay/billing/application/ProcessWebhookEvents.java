package com.tenantpay.billing.application;

import com.tenantpay.billing.application.exception.NotFoundException;
import com.tenantpay.billing.application.port.ChargeRepository;
import com.tenantpay.billing.application.port.SubscriptionRepository;
import com.tenantpay.billing.application.port.UnitOfWork;
import com.tenantpay.billing.application.port.WebhookEventRepository;
import com.tenantpay.billing.domain.Charge;
import com.tenantpay.billing.domain.ChargeStatus;
import com.tenantpay.billing.domain.RetryPolicy;
import com.tenantpay.billing.domain.Subscription;
import com.tenantpay.billing.domain.WebhookEvent;
import com.tenantpay.billing.domain.WebhookEventStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Drains the webhook inbox. One call claims a batch of due events in a short transaction (row locks with SKIP
 * LOCKED, so several instances can run side by side), then applies each event in its own transaction. A failure
 * rolls back that event's changes and records the attempt with exponential backoff; when the retry policy is
 * exhausted the event becomes DEAD and waits for an operator.
 *
 * <p>Every handler is idempotent: re-applying a payment that was already applied changes nothing.
 */
public class ProcessWebhookEvents {
    public static final String PAYMENT_CONFIRMED = "PAYMENT_CONFIRMED";
    public static final String PAYMENT_RECEIVED = "PAYMENT_RECEIVED";
    public static final String PAYMENT_OVERDUE = "PAYMENT_OVERDUE";

    private final WebhookEventRepository events;
    private final ChargeRepository charges;
    private final SubscriptionRepository subscriptions;
    private final UnitOfWork uow;
    private final Clock clock;
    private final RetryPolicy retryPolicy;
    private final int batchSize;
    private final Duration lease;

    public ProcessWebhookEvents(WebhookEventRepository events, ChargeRepository charges,
                                SubscriptionRepository subscriptions, UnitOfWork uow, Clock clock,
                                RetryPolicy retryPolicy, int batchSize, Duration lease) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        this.events = events;
        this.charges = charges;
        this.subscriptions = subscriptions;
        this.uow = uow;
        this.clock = clock;
        this.retryPolicy = retryPolicy;
        this.batchSize = batchSize;
        this.lease = lease;
    }

    /** Processes one batch of due events and returns how many were claimed. */
    public int processDue() {
        Instant now = clock.instant();
        List<UUID> claimed = uow.inTransaction(() -> {
            List<WebhookEvent> due = events.lockDue(now, batchSize);
            for (WebhookEvent event : due) {
                event.startProcessing(now, lease);
                events.save(event);
            }
            return due.stream().map(WebhookEvent::id).toList();
        });
        claimed.forEach(this::processOne);
        return claimed.size();
    }

    private void processOne(UUID id) {
        try {
            uow.run(() -> handle(id));
        } catch (RuntimeException failure) {
            uow.run(() -> events.findById(id)
                    .filter(event -> event.status() == WebhookEventStatus.PROCESSING)
                    .ifPresent(event -> {
                        event.markFailed(describe(failure), clock.instant(), retryPolicy);
                        events.save(event);
                    }));
        }
    }

    private void handle(UUID id) {
        WebhookEvent event = events.findById(id).orElseThrow(() -> NotFoundException.of("Webhook event", id));
        if (event.status() != WebhookEventStatus.PROCESSING) {
            return;
        }
        Instant now = clock.instant();
        switch (event.eventType()) {
            case PAYMENT_CONFIRMED, PAYMENT_RECEIVED, PAYMENT_OVERDUE -> {
                applyPayment(event, now);
                event.markProcessed(now);
            }
            default -> event.markIgnored(now);
        }
        events.save(event);
    }

    private void applyPayment(WebhookEvent event, Instant now) {
        if (event.paymentId() == null || event.paymentId().isBlank()) {
            throw new IllegalStateException("Event " + event.eventId() + " has no payment id");
        }
        Charge charge = charges.findByGatewayPaymentId(event.paymentId())
                .orElseThrow(() -> new NotFoundException("No charge for gateway payment " + event.paymentId()));
        event.assignTenant(charge.tenantId());
        Subscription subscription = subscriptions.findById(charge.tenantId(), charge.subscriptionId())
                .orElseThrow(() -> NotFoundException.of("Subscription", charge.subscriptionId()));

        switch (event.eventType()) {
            case PAYMENT_CONFIRMED -> {
                charge.confirm(now);
                activateUnlessCanceled(subscription, now);
            }
            case PAYMENT_RECEIVED -> {
                charge.receive(now);
                activateUnlessCanceled(subscription, now);
            }
            case PAYMENT_OVERDUE -> {
                // A late notification about a charge that is already paid or canceled must not undo that state.
                if (charge.status() == ChargeStatus.PENDING || charge.status() == ChargeStatus.OVERDUE) {
                    charge.markOverdue();
                    if (!subscription.isCanceled()) {
                        subscription.markOverdue();
                    }
                }
            }
            default -> throw new IllegalStateException("Unsupported payment event " + event.eventType());
        }
        charges.save(charge);
        subscriptions.save(subscription);
    }

    private static void activateUnlessCanceled(Subscription subscription, Instant now) {
        if (!subscription.isCanceled()) {
            subscription.activate(now);
        }
    }

    private static String describe(RuntimeException failure) {
        String message = failure.getMessage();
        return failure.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }
}
