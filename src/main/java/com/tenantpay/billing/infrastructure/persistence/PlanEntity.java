package com.tenantpay.billing.infrastructure.persistence;

import com.tenantpay.billing.domain.Money;
import com.tenantpay.billing.domain.Plan;
import com.tenantpay.billing.domain.PlanInterval;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "plans")
public class PlanEntity {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(nullable = false, length = 120)
    private String name;
    @Column(length = 500)
    private String description;
    @Column(name = "amount_cents", nullable = false)
    private long amountCents;
    @Column(nullable = false, length = 3)
    private String currency;
    @Enumerated(EnumType.STRING)
    @Column(name = "billing_interval", nullable = false, length = 10)
    private PlanInterval interval;
    @Column(nullable = false)
    private boolean active;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PlanEntity() {
    }

    static PlanEntity from(Plan plan) {
        PlanEntity entity = new PlanEntity();
        entity.id = plan.id();
        entity.tenantId = plan.tenantId();
        entity.name = plan.name();
        entity.description = plan.description();
        entity.amountCents = plan.price().amountCents();
        entity.currency = plan.price().currency();
        entity.interval = plan.interval();
        entity.active = plan.active();
        entity.createdAt = plan.createdAt();
        return entity;
    }

    Plan toDomain() {
        return Plan.restore(id, tenantId, name, description, new Money(amountCents, currency), interval, active,
                createdAt);
    }
}
