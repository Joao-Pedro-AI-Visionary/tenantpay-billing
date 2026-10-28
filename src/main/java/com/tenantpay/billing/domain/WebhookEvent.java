package com.tenantpay.billing.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Inbox entry for one gateway notification. The event id from the gateway is unique, which makes ingestion
 * idempotent; processing happens later and is retried with backoff.
 *
 * <p>Lifecycle: RECEIVED to PROCESSING (claimed by a worker, attempts incremented, {@code nextAttemptAt} holds the
 * lease expiry), then PROCESSED, IGNORED, FAILED (retry at {@code nextAttemptAt}) or DEAD (attempts exhausted).
 * FAILED and DEAD events can be reset to RECEIVED by an operator.
 */
public final class WebhookEvent {
    public static final int MAX_ERROR_LENGTH = 2000;

    private final UUID id;
    private UUID tenantId;
    private final String eventId;
    private final String eventType;
    private final String paymentId;
    private final String payload;
    private WebhookEventStatus status;
    private int attempts;
    private Instant nextAttemptAt;
    private String lastError;
    private final Instant receivedAt;
    private Instant processedAt;

    private WebhookEvent(UUID id, UUID tenantId, String eventId, String eventType, String paymentId, String payload,
                         WebhookEventStatus status, int attempts, Instant nextAttemptAt, String lastError,
                         Instant receivedAt, Instant processedAt) {
        this.id = Guard.notNull(id, "id");
        this.tenantId = tenantId;
        this.eventId = Guard.notBlank(eventId, "eventId", 255);
        this.eventType = Guard.notBlank(eventType, "eventType", 64);
        this.paymentId = paymentId;
        this.payload = payload;
        this.status = Guard.notNull(status, "status");
        if (attempts < 0) {
            throw new DomainValidationException("attempts must not be negative");
        }
        this.attempts = attempts;
        this.nextAttemptAt = nextAttemptAt;
        this.lastError = lastError;
        this.receivedAt = Guard.notNull(receivedAt, "receivedAt");
        this.processedAt = processedAt;
    }

    public static WebhookEvent receive(String eventId, String eventType, String paymentId, String payload,
                                       UUID tenantId, Instant now) {
        if (payload == null || payload.isBlank()) {
            throw new DomainValidationException("payload is required");
        }
        return new WebhookEvent(UUID.randomUUID(), tenantId, eventId, eventType, paymentId, payload,
                WebhookEventStatus.RECEIVED, 0, null, null, now, null);
    }

    /** Rehydrates an event from storage. The raw payload is optional because processing does not need it. */
    public static WebhookEvent restore(UUID id, UUID tenantId, String eventId, String eventType, String paymentId,
                                       String payload, WebhookEventStatus status, int attempts,
                                       Instant nextAttemptAt, String lastError, Instant receivedAt,
                                       Instant processedAt) {
        return new WebhookEvent(id, tenantId, eventId, eventType, paymentId, payload, status, attempts,
                nextAttemptAt, lastError, receivedAt, processedAt);
    }

    /** Claims the event for one processing attempt. The lease protects against workers that crash mid-way. */
    public void startProcessing(Instant now, Duration lease) {
        boolean claimable = status == WebhookEventStatus.RECEIVED
                || status == WebhookEventStatus.FAILED
                || (status == WebhookEventStatus.PROCESSING && nextAttemptAt != null && !nextAttemptAt.isAfter(now));
        if (!claimable) {
            throw new InvalidStateTransitionException("Webhook event " + eventId, status, "start processing");
        }
        status = WebhookEventStatus.PROCESSING;
        attempts++;
        nextAttemptAt = now.plus(lease);
    }

    /**
     * True when a worker crashed (the lease expired) on the last allowed attempt. Such an event must not be claimed
     * again: an event that kills the worker every time would otherwise loop forever.
     */
    public boolean leaseExpiredOnLastAttempt(Instant now, RetryPolicy policy) {
        return status == WebhookEventStatus.PROCESSING && nextAttemptAt != null && !nextAttemptAt.isAfter(now)
                && attempts >= policy.maxAttempts();
    }

    /** Moves an event whose final attempt crashed to DEAD. */
    public void markAbandoned(Instant now) {
        requireProcessing("be abandoned");
        status = WebhookEventStatus.DEAD;
        lastError = "Worker lease expired on attempt " + attempts + " of the retry budget";
        nextAttemptAt = null;
    }

    public void markProcessed(Instant now) {
        finish(WebhookEventStatus.PROCESSED, now);
    }

    public void markIgnored(Instant now) {
        finish(WebhookEventStatus.IGNORED, now);
    }

    private void finish(WebhookEventStatus target, Instant now) {
        requireProcessing("become " + target);
        status = target;
        processedAt = now;
        nextAttemptAt = null;
        lastError = null;
    }

    /** Records a failed attempt: FAILED with a backoff, or DEAD when the policy is exhausted. */
    public void markFailed(String error, Instant now, RetryPolicy policy) {
        requireProcessing("fail");
        lastError = truncate(error == null || error.isBlank() ? "unknown error" : error);
        if (policy.isExhausted(attempts)) {
            status = WebhookEventStatus.DEAD;
            nextAttemptAt = null;
        } else {
            status = WebhookEventStatus.FAILED;
            nextAttemptAt = now.plus(policy.backoffFor(attempts));
        }
    }

    /** Operator action: give a FAILED or DEAD event a fresh set of attempts. */
    public void resetForReprocess() {
        if (status != WebhookEventStatus.FAILED && status != WebhookEventStatus.DEAD) {
            throw new InvalidStateTransitionException("Webhook event " + eventId, status, "be reprocessed");
        }
        status = WebhookEventStatus.RECEIVED;
        attempts = 0;
        nextAttemptAt = null;
        processedAt = null;
    }

    /** Binds an event that arrived before its charge was known to the tenant that owns the charge. */
    public void assignTenant(UUID owner) {
        if (tenantId == null) {
            tenantId = owner;
        } else if (!tenantId.equals(owner)) {
            throw new DomainValidationException("webhook event already belongs to another tenant");
        }
    }

    private void requireProcessing(String action) {
        if (status != WebhookEventStatus.PROCESSING) {
            throw new InvalidStateTransitionException("Webhook event " + eventId, status, action);
        }
    }

    private static String truncate(String value) {
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public String eventId() {
        return eventId;
    }

    public String eventType() {
        return eventType;
    }

    public String paymentId() {
        return paymentId;
    }

    public String payload() {
        return payload;
    }

    public WebhookEventStatus status() {
        return status;
    }

    public int attempts() {
        return attempts;
    }

    public Instant nextAttemptAt() {
        return nextAttemptAt;
    }

    public String lastError() {
        return lastError;
    }

    public Instant receivedAt() {
        return receivedAt;
    }

    public Instant processedAt() {
        return processedAt;
    }
}
