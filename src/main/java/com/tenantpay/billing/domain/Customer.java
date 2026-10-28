package com.tenantpay.billing.domain;

import java.time.Instant;
import java.util.UUID;

/** The payer of a subscription. Mirrored in the payment gateway. */
public final class Customer {
    private final UUID id;
    private final UUID tenantId;
    private final String name;
    private final String email;
    private final String document;
    private final String gatewayCustomerId;
    private final Instant createdAt;

    private Customer(UUID id, UUID tenantId, String name, String email, String document, String gatewayCustomerId,
                     Instant createdAt) {
        this.id = Guard.notNull(id, "id");
        this.tenantId = Guard.notNull(tenantId, "tenantId");
        this.name = Guard.notBlank(name, "name", 120);
        this.email = User.normalizeEmail(email);
        this.document = Guard.notBlank(document, "document", 14);
        if (!this.document.matches("\\d{11}|\\d{14}")) {
            throw new DomainValidationException("document must have 11 or 14 digits");
        }
        this.gatewayCustomerId = Guard.notBlank(gatewayCustomerId, "gatewayCustomerId", 100);
        this.createdAt = Guard.notNull(createdAt, "createdAt");
    }

    public static Customer create(UUID id, UUID tenantId, String name, String email, String document,
                                  String gatewayCustomerId, Instant now) {
        return new Customer(id, tenantId, name, email, document, gatewayCustomerId, now);
    }

    public static Customer restore(UUID id, UUID tenantId, String name, String email, String document,
                                   String gatewayCustomerId, Instant createdAt) {
        return new Customer(id, tenantId, name, email, document, gatewayCustomerId, createdAt);
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

    public String email() {
        return email;
    }

    public String document() {
        return document;
    }

    public String gatewayCustomerId() {
        return gatewayCustomerId;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
