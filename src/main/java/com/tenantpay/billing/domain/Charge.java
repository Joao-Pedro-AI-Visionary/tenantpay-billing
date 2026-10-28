package com.tenantpay.billing.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One payment request of a subscription, mirrored in the payment gateway.
 *
 * <p>State machine: PENDING or OVERDUE to CONFIRMED (card approved, not settled yet), PENDING, OVERDUE or CONFIRMED
 * to RECEIVED (money settled), PENDING to OVERDUE, and unpaid charges to CANCELED. Re-applying the current status is a
 * no-op; a paid charge is never downgraded.
 */
public final class Charge {
    private final UUID id;
    private final UUID tenantId;
    private final UUID subscriptionId;
    private final UUID customerId;
    private final String gatewayPaymentId;
    private final long amountCents;
    private ChargeStatus status;
    private final LocalDate dueDate;
    private Instant paidAt;
    private final Instant createdAt;

    private Charge(UUID id, UUID tenantId, UUID subscriptionId, UUID customerId, String gatewayPaymentId,
                   long amountCents, ChargeStatus status, LocalDate dueDate, Instant paidAt, Instant createdAt) {
        this.id = Guard.notNull(id, "id");
        this.tenantId = Guard.notNull(tenantId, "tenantId");
        this.subscriptionId = Guard.notNull(subscriptionId, "subscriptionId");
        this.customerId = Guard.notNull(customerId, "customerId");
        this.gatewayPaymentId = Guard.notBlank(gatewayPaymentId, "gatewayPaymentId", 100);
        if (amountCents <= 0) {
            throw new DomainValidationException("amountCents must be positive");
        }
        this.amountCents = amountCents;
        this.status = Guard.notNull(status, "status");
        this.dueDate = Guard.notNull(dueDate, "dueDate");
        this.paidAt = paidAt;
        this.createdAt = Guard.notNull(createdAt, "createdAt");
    }

    public static Charge create(UUID id, Subscription subscription, String gatewayPaymentId, LocalDate dueDate,
                                Instant now) {
        return new Charge(id, subscription.tenantId(), subscription.id(), subscription.customerId(),
                gatewayPaymentId, subscription.amountCents(), ChargeStatus.PENDING, dueDate, null, now);
    }

    public static Charge restore(UUID id, UUID tenantId, UUID subscriptionId, UUID customerId,
                                 String gatewayPaymentId, long amountCents, ChargeStatus status, LocalDate dueDate,
                                 Instant paidAt, Instant createdAt) {
        return new Charge(id, tenantId, subscriptionId, customerId, gatewayPaymentId, amountCents, status, dueDate,
                paidAt, createdAt);
    }

    public void confirm(Instant paidAt) {
        switch (status) {
            case CONFIRMED, RECEIVED -> {
                // already paid: no-op
            }
            case PENDING, OVERDUE -> {
                status = ChargeStatus.CONFIRMED;
                this.paidAt = paidAt;
            }
            case CANCELED -> throw new InvalidStateTransitionException("Charge " + id, status, "be confirmed");
        }
    }

    public void receive(Instant paidAt) {
        switch (status) {
            case RECEIVED -> {
                // already settled: no-op
            }
            case PENDING, OVERDUE, CONFIRMED -> {
                status = ChargeStatus.RECEIVED;
                if (this.paidAt == null) {
                    this.paidAt = paidAt;
                }
            }
            case CANCELED -> throw new InvalidStateTransitionException("Charge " + id, status, "be received");
        }
    }

    public void markOverdue() {
        switch (status) {
            case OVERDUE -> {
                // already overdue: no-op
            }
            case PENDING -> status = ChargeStatus.OVERDUE;
            case CONFIRMED, RECEIVED, CANCELED ->
                    throw new InvalidStateTransitionException("Charge " + id, status, "become overdue");
        }
    }

    public void cancel() {
        switch (status) {
            case CANCELED -> {
                // already canceled: no-op
            }
            case PENDING, OVERDUE -> status = ChargeStatus.CANCELED;
            case CONFIRMED, RECEIVED -> throw new InvalidStateTransitionException("Charge " + id, status, "cancel");
        }
    }

    public boolean isOpen() {
        return status == ChargeStatus.PENDING || status == ChargeStatus.OVERDUE;
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public UUID subscriptionId() {
        return subscriptionId;
    }

    public UUID customerId() {
        return customerId;
    }

    public String gatewayPaymentId() {
        return gatewayPaymentId;
    }

    public long amountCents() {
        return amountCents;
    }

    public ChargeStatus status() {
        return status;
    }

    public LocalDate dueDate() {
        return dueDate;
    }

    public Instant paidAt() {
        return paidAt;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
