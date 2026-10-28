package com.tenantpay.billing.infrastructure.persistence;

import com.tenantpay.billing.domain.Charge;
import com.tenantpay.billing.domain.ChargeStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "charges")
public class ChargeEntity {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;
    @Column(name = "gateway_payment_id", nullable = false, length = 100)
    private String gatewayPaymentId;
    @Column(name = "amount_cents", nullable = false)
    private long amountCents;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChargeStatus status;
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;
    @Column(name = "paid_at")
    private Instant paidAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ChargeEntity() {
    }

    static ChargeEntity from(Charge charge) {
        ChargeEntity entity = new ChargeEntity();
        entity.id = charge.id();
        entity.tenantId = charge.tenantId();
        entity.subscriptionId = charge.subscriptionId();
        entity.customerId = charge.customerId();
        entity.gatewayPaymentId = charge.gatewayPaymentId();
        entity.amountCents = charge.amountCents();
        entity.status = charge.status();
        entity.dueDate = charge.dueDate();
        entity.paidAt = charge.paidAt();
        entity.createdAt = charge.createdAt();
        return entity;
    }

    Charge toDomain() {
        return Charge.restore(id, tenantId, subscriptionId, customerId, gatewayPaymentId, amountCents, status,
                dueDate, paidAt, createdAt);
    }
}
