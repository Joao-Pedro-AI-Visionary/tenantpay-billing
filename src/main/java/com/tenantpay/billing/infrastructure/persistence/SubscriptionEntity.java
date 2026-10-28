package com.tenantpay.billing.infrastructure.persistence;

import com.tenantpay.billing.domain.Subscription;
import com.tenantpay.billing.domain.SubscriptionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
public class SubscriptionEntity {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;
    @Column(name = "plan_id", nullable = false)
    private UUID planId;
    @Column(name = "amount_cents", nullable = false)
    private long amountCents;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionStatus status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "activated_at")
    private Instant activatedAt;
    @Column(name = "canceled_at")
    private Instant canceledAt;

    protected SubscriptionEntity() {
    }

    static SubscriptionEntity from(Subscription subscription) {
        SubscriptionEntity entity = new SubscriptionEntity();
        entity.id = subscription.id();
        entity.tenantId = subscription.tenantId();
        entity.customerId = subscription.customerId();
        entity.planId = subscription.planId();
        entity.amountCents = subscription.amountCents();
        entity.status = subscription.status();
        entity.createdAt = subscription.createdAt();
        entity.activatedAt = subscription.activatedAt();
        entity.canceledAt = subscription.canceledAt();
        return entity;
    }

    Subscription toDomain() {
        return Subscription.restore(id, tenantId, customerId, planId, amountCents, status, createdAt, activatedAt,
                canceledAt);
    }
}
