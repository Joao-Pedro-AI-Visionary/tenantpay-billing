package com.tenantpay.billing.application;

import com.tenantpay.billing.application.port.ChargeRepository;
import com.tenantpay.billing.application.port.CustomerRepository;
import com.tenantpay.billing.application.port.PlanRepository;
import com.tenantpay.billing.application.port.SubscriptionRepository;
import com.tenantpay.billing.application.port.UnitOfWork;
import com.tenantpay.billing.domain.SubscriptionStatus;

import java.util.List;
import java.util.UUID;

public class ListSubscriptions {
    private final SubscriptionRepository subscriptions;
    private final SubscriptionViews views;
    private final UnitOfWork uow;

    public ListSubscriptions(SubscriptionRepository subscriptions, CustomerRepository customers,
                             PlanRepository plans, ChargeRepository charges, UnitOfWork uow) {
        this.subscriptions = subscriptions;
        this.views = new SubscriptionViews(customers, plans, charges);
        this.uow = uow;
    }

    /** {@code status} is optional. */
    public List<SubscriptionView> execute(UUID tenantId, SubscriptionStatus status) {
        return uow.inTransaction(() -> views.assemble(tenantId, subscriptions.findAll(tenantId, status)));
    }
}
