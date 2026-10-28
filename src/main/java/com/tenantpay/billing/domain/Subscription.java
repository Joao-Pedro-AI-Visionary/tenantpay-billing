package com.tenantpay.billing.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A customer subscribed to a plan.
 *
 * <p>State machine: PENDING to ACTIVE when a payment is confirmed, ACTIVE or PENDING to OVERDUE when a payment is
 * late, OVERDUE back to ACTIVE when paid, and any non canceled status to CANCELED. Repeating the transition to the
 * current status is a no-op so that webhook processing stays idempotent.
 */
public final class Subscription {
    private final UUID id;
    private final UUID tenantId;
    private final UUID customerId;
    private final UUID planId;
    private final long amountCents;
    private SubscriptionStatus status;
    private final Instant createdAt;
    private Instant activatedAt;
    private Instant canceledAt;

    private Subscription(UUID id, UUID tenantId, UUID customerId, UUID planId, long amountCents,
                         SubscriptionStatus status, Instant createdAt, Instant activatedAt, Instant canceledAt) {
        this.id = Guard.notNull(id, "id");
        this.tenantId = Guard.notNull(tenantId, "tenantId");
        this.customerId = Guard.notNull(customerId, "customerId");
        this.planId = Guard.notNull(planId, "planId");
        if (amountCents < Plan.MIN_AMOUNT_CENTS) {
            throw new DomainValidationException("amountCents must be at least " + Plan.MIN_AMOUNT_CENTS);
        }
        this.amountCents = amountCents;
        this.status = Guard.notNull(status, "status");
        this.createdAt = Guard.notNull(createdAt, "createdAt");
        this.activatedAt = activatedAt;
        this.canceledAt = canceledAt;
    }

    public static Subscription start(UUID tenantId, Customer customer, Plan plan, Instant now) {
        if (!customer.tenantId().equals(tenantId) || !plan.tenantId().equals(tenantId)) {
            throw new DomainValidationException("customer and plan must belong to the subscription tenant");
        }
        plan.ensureSubscribable();
        return new Subscription(UUID.randomUUID(), tenantId, customer.id(), plan.id(), plan.price().amountCents(),
                SubscriptionStatus.PENDING, now, null, null);
    }

    public static Subscription restore(UUID id, UUID tenantId, UUID customerId, UUID planId, long amountCents,
                                       SubscriptionStatus status, Instant createdAt, Instant activatedAt,
                                       Instant canceledAt) {
        return new Subscription(id, tenantId, customerId, planId, amountCents, status, createdAt, activatedAt,
                canceledAt);
    }

    /** Returns true when the status changed, false when it was already ACTIVE. */
    public boolean activate(Instant now) {
        return switch (status) {
            case ACTIVE -> false;
            case PENDING, OVERDUE -> {
                status = SubscriptionStatus.ACTIVE;
                if (activatedAt == null) {
                    activatedAt = now;
                }
                yield true;
            }
            case CANCELED -> throw new InvalidStateTransitionException("Subscription " + id, status, "activate");
        };
    }

    /** Returns true when the status changed, false when it was already OVERDUE. */
    public boolean markOverdue() {
        return switch (status) {
            case OVERDUE -> false;
            case PENDING, ACTIVE -> {
                status = SubscriptionStatus.OVERDUE;
                yield true;
            }
            case CANCELED -> throw new InvalidStateTransitionException("Subscription " + id, status,
                    "become overdue");
        };
    }

    public void cancel(Instant now) {
        if (status == SubscriptionStatus.CANCELED) {
            throw new InvalidStateTransitionException("Subscription " + id, status, "cancel");
        }
        status = SubscriptionStatus.CANCELED;
        canceledAt = now;
    }

    public boolean isCanceled() {
        return status == SubscriptionStatus.CANCELED;
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public UUID customerId() {
        return customerId;
    }

    public UUID planId() {
        return planId;
    }

    public long amountCents() {
        return amountCents;
    }

    public SubscriptionStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant activatedAt() {
        return activatedAt;
    }

    public Instant canceledAt() {
        return canceledAt;
    }
}
