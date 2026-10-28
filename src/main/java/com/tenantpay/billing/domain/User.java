package com.tenantpay.billing.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/** A person that can log into the panel of one tenant. */
public final class User {
    private final UUID id;
    private final UUID tenantId;
    private final String email;
    private final String passwordHash;
    private final Role role;
    private final Instant createdAt;

    private User(UUID id, UUID tenantId, String email, String passwordHash, Role role, Instant createdAt) {
        this.id = Guard.notNull(id, "id");
        this.tenantId = Guard.notNull(tenantId, "tenantId");
        this.email = normalizeEmail(email);
        this.passwordHash = Guard.notBlank(passwordHash, "passwordHash", 200);
        this.role = Guard.notNull(role, "role");
        this.createdAt = Guard.notNull(createdAt, "createdAt");
    }

    public static User createAdmin(UUID tenantId, String email, String passwordHash, Instant now) {
        return new User(UUID.randomUUID(), tenantId, email, passwordHash, Role.ADMIN, now);
    }

    public static User restore(UUID id, UUID tenantId, String email, String passwordHash, Role role,
                               Instant createdAt) {
        return new User(id, tenantId, email, passwordHash, role, createdAt);
    }

    public static String normalizeEmail(String email) {
        return Guard.notBlank(email, "email", 254).toLowerCase(Locale.ROOT);
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public String email() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public Role role() {
        return role;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
