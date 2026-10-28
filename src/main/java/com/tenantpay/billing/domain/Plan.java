package com.tenantpay.billing.domain;

import java.time.Instant;
import java.util.UUID;

/** A price list entry that customers can subscribe to. */
public final class Plan {
    public static final long MIN_AMOUNT_CENTS = 100;

    private final UUID id;
    private final UUID tenantId;
    private String name;
    private String description;
    private Money price;
    private PlanInterval interval;
    private boolean active;
    private final Instant createdAt;

    private Plan(UUID id, UUID tenantId, String name, String description, Money price, PlanInterval interval,
                 boolean active, Instant createdAt) {
        this.id = Guard.notNull(id, "id");
        this.tenantId = Guard.notNull(tenantId, "tenantId");
        this.createdAt = Guard.notNull(createdAt, "createdAt");
        this.active = active;
        apply(name, description, price, interval);
    }

    public static Plan create(UUID tenantId, String name, String description, long amountCents,
                              PlanInterval interval, Instant now) {
        return new Plan(UUID.randomUUID(), tenantId, name, description, Money.brl(amountCents), interval, true, now);
    }

    public static Plan restore(UUID id, UUID tenantId, String name, String description, Money price,
                               PlanInterval interval, boolean active, Instant createdAt) {
        return new Plan(id, tenantId, name, description, price, interval, active, createdAt);
    }

    public void update(String name, String description, long amountCents, PlanInterval interval) {
        apply(name, description, Money.brl(amountCents), interval);
    }

    private void apply(String name, String description, Money price, PlanInterval interval) {
        String cleanName = Guard.notBlank(name, "name", 120);
        Guard.notNull(price, "price");
        if (price.amountCents() < MIN_AMOUNT_CENTS) {
            throw new DomainValidationException("amountCents must be at least " + MIN_AMOUNT_CENTS);
        }
        Guard.notNull(interval, "interval");
        String cleanDescription = description == null || description.isBlank() ? null : description.trim();
        if (cleanDescription != null && cleanDescription.length() > 500) {
            throw new DomainValidationException("description must have at most 500 characters");
        }
        this.name = cleanName;
        this.description = cleanDescription;
        this.price = price;
        this.interval = interval;
    }

    /** Soft delete. Idempotent. */
    public void deactivate() {
        this.active = false;
    }

    /** Inactive plans cannot receive new subscriptions. */
    public void ensureSubscribable() {
        if (!active) {
            throw new InvalidStateTransitionException("Plan " + id, "INACTIVE", "accept subscriptions");
        }
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public Money price() {
        return price;
    }

    public PlanInterval interval() {
        return interval;
    }

    public boolean active() {
        return active;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
