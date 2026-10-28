package com.tenantpay.billing.application;

import com.tenantpay.billing.application.port.ChargeRepository;
import com.tenantpay.billing.application.port.CustomerRepository;
import com.tenantpay.billing.application.port.PlanRepository;
import com.tenantpay.billing.application.port.SubscriptionRepository;
import com.tenantpay.billing.application.port.UnitOfWork;
import com.tenantpay.billing.domain.SubscriptionStatus;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/** Headline numbers of one tenant. {@code plans} counts active plans only. */
public class GetDashboard {
    private final PlanRepository plans;
    private final CustomerRepository customers;
    private final SubscriptionRepository subscriptions;
    private final ChargeRepository charges;
    private final UnitOfWork uow;

    public GetDashboard(PlanRepository plans, CustomerRepository customers, SubscriptionRepository subscriptions,
                        ChargeRepository charges, UnitOfWork uow) {
        this.plans = plans;
        this.customers = customers;
        this.subscriptions = subscriptions;
        this.charges = charges;
        this.uow = uow;
    }

    public View execute(UUID tenantId) {
        return uow.inTransaction(() -> {
            Map<SubscriptionStatus, Long> byStatus = new EnumMap<>(SubscriptionStatus.class);
            for (SubscriptionStatus status : SubscriptionStatus.values()) {
                byStatus.put(status, 0L);
            }
            byStatus.putAll(subscriptions.countByStatus(tenantId));
            long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
            return new View(plans.countActive(tenantId), customers.count(tenantId), total, byStatus,
                    charges.sumPaidAmountCents(tenantId));
        });
    }

    public record View(long plans, long customers, long subscriptions,
                       Map<SubscriptionStatus, Long> subscriptionsByStatus, long receivedAmountCents) {
    }
}
