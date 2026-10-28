package com.tenantpay.billing.domain;

import java.time.Instant;
import java.util.UUID;

/** A customer account of the platform. Every other aggregate belongs to exactly one tenant. */
public final class Tenant {
    private final UUID id;
    private final String name;
    private final Instant createdAt;

    private Tenant(UUID id, String name, Instant createdAt) {
        this.id = Guard.notNull(id, "id");
        this.name = Guard.notBlank(name, "tenantName", 120);
        this.createdAt = Guard.notNull(createdAt, "createdAt");
    }

    public static Tenant create(String name, Instant now) {
        return new Tenant(UUID.randomUUID(), name, now);
    }

    public static Tenant restore(UUID id, String name, Instant createdAt) {
        return new Tenant(id, name, createdAt);
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
