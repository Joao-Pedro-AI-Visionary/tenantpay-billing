package com.tenantpay.billing.application;

import com.tenantpay.billing.application.exception.NotFoundException;
import com.tenantpay.billing.application.port.ChargeRepository;
import com.tenantpay.billing.application.port.CustomerRepository;
import com.tenantpay.billing.application.port.PlanRepository;
import com.tenantpay.billing.application.port.SubscriptionRepository;
import com.tenantpay.billing.application.port.UnitOfWork;
import com.tenantpay.billing.domain.Subscription;

import java.util.UUID;

public class GetSubscription {
    private final SubscriptionRepository subscriptions;
    private final SubscriptionViews views;
    private final UnitOfWork uow;

    public GetSubscription(SubscriptionRepository subscriptions, CustomerRepository customers,
                           PlanRepository plans, ChargeRepository charges, UnitOfWork uow) {
        this.subscriptions = subscriptions;
        this.views = new SubscriptionViews(customers, plans, charges);
        this.uow = uow;
    }

    public SubscriptionView execute(UUID tenantId, UUID subscriptionId) {
        return uow.inTransaction(() -> {
            Subscription subscription = subscriptions.findById(tenantId, subscriptionId)
                    .orElseThrow(() -> NotFoundException.of("Subscription", subscriptionId));
            return views.assemble(tenantId, subscription);
        });
    }
}
